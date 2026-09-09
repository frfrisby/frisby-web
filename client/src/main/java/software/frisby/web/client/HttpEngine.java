package software.frisby.web.client;

import software.frisby.core.util.StopWatch;
import software.frisby.core.validation.Values;
import software.frisby.web.client.event.ClientEventListener;
import software.frisby.web.client.event.RequestCompletedEvent;
import software.frisby.web.client.event.RequestFailedEvent;
import software.frisby.web.client.exception.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * The execution engine for the HTTP client.
 * <p>
 * Owns and configures the underlying JDK {@link HttpClient}, executes requests
 * (synchronously and asynchronously), measures latency, fires
 * {@link ClientEventListener} events, and logs at the appropriate level.
 * <p>
 * Verb-specific request types ({@link GetRequest}, etc.) build the {@link HttpRequest}
 * and choose the {@link HttpResponse.BodyHandler}, then delegate execution here.
 * <p>
 * A single {@link ExecutorService} is shared across all {@code HttpEngine} instances
 * that do not specify a custom executor.  This prevents thread-pool proliferation when
 * many client instances are created (e.g. in test harnesses).
 */
final class HttpEngine {
    private static final System.Logger LOGGER = System.getLogger(HttpEngine.class.getName());
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newCachedThreadPool();

    // A dedicated scheduler is used for async retry delays rather than the caller-supplied
    // executor for two reasons:
    //
    //   1. The caller's executor may be a fixed-size platform thread pool — sleeping inside
    //      it for the retry delay would hold a thread from the pool for the full wait, which
    //      could starve concurrent requests.
    //   2. Using Thread.sleep() on a virtual-thread executor (Java 21+) would be safe there,
    //      but we cannot detect at runtime whether the supplied executor creates virtual or
    //      platform threads while targeting Java 17.
    //
    // A ScheduledExecutorService avoids both problems: the timer is OS-backed, no thread is
    // held during the delay, and the scheduler's platform thread is occupied only for the
    // few microseconds it takes to invoke the next retryAsync() call.
    private static final ScheduledExecutorService DEFAULT_RETRY_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "frisby-retry-scheduler");
                t.setDaemon(true);
                return t;
            });

    private final HttpClient httpClient;
    private final ClientConfiguration configuration;
    private final ClientEventListener eventListener;
    private final RequestLogger requestLogger;
    private final RetryPolicy retryPolicy;

    HttpEngine(ClientConfiguration configuration, ClientEventListener eventListener, RetryPolicy retryPolicy) {
        this.configuration = Values.notNull("configuration", configuration);
        this.eventListener = Values.notNull("eventListener", eventListener);
        this.retryPolicy = Values.notNull("retryPolicy", retryPolicy);
        this.requestLogger = new RequestLogger(configuration.logging());

        this.httpClient = buildHttpClient(configuration);
    }

    static Throwable unwrapCompletionException(Throwable cause) {
        if (cause instanceof CompletionException) {
            return cause.getCause();
        }

        return cause;
    }

    static Throwable unwrapHttpResponseException(Throwable cause) {
        if (cause instanceof IOException ioe
                && ioe.getCause() instanceof HttpResponseException hre) {
            return hre;
        }

        return cause;
    }

    static Throwable wrapIfIOException(Throwable cause, String method, URI uri) {
        if (cause instanceof IOException) {
            cause = new TransportException(cause, method, uri);
        }

        return cause;
    }

    private static HttpClient buildHttpClient(ClientConfiguration configuration) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(configuration.connectTimeout())
                .followRedirects(configuration.redirectPolicy())
                .version(configuration.httpVersion());

        configuration.sslContext().ifPresent(builder::sslContext);

        Executor executor = configuration.executor().orElse(DEFAULT_EXECUTOR);
        builder.executor(executor);

        return builder.build();
    }

    /**
     * Annotates the event with the retry attempt if {@code retryAttempt > 1}; returns the base event otherwise.
     */
    private static RequestFailedEvent buildFailedEvent(RequestFailedEvent base, int retryAttempt) {
        return retryAttempt > 1 ? base.withRetryAttempt(retryAttempt) : base;
    }


    /**
     * Returns the {@link ClientConfiguration} used to create this engine.
     *
     * @return The client configuration.
     */
    ClientConfiguration configuration() {
        return configuration;
    }

    /**
     * Executes {@code httpClient.send()} and maps all JDK-specific checked exceptions to
     * the client exception hierarchy.  Returns the response on success; always throws a
     * {@link RuntimeException} subtype on failure.
     */
    private <T> HttpResponse<T> executeRequest(HttpRequest request,
                                               HttpResponse.BodyHandler<T> bodyHandler) {
        try {
            return httpClient.send(request, bodyHandler);
        } catch (HttpConnectTimeoutException ex) {
            throw new ConnectTimeoutException(ex, request.method(), request.uri());
        } catch (HttpTimeoutException ex) {
            throw new ReadTimeoutException(ex, request.method(), request.uri());
        } catch (java.net.ConnectException ex) {
            throw new software.frisby.web.client.exception.ConnectException(
                    ex, request.method(), request.uri()
            );
        } catch (IOException ex) {
            // The JDK HttpClient wraps RuntimeExceptions thrown from body handler mapping
            // functions in a plain IOException.  Unwrap HttpResponseException so callers
            // receive the correctly-typed HTTP error exception.
            if (ex.getCause() instanceof HttpResponseException hre) {
                throw hre;
            }

            throw new TransportException(ex, request.method(), request.uri());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AbortedException(ex, request.method(), request.uri());
        }
    }

    /**
     * Sends an HTTP request, retrying on failure according to the configured retry policy.
     * <p>
     * The supplier is called at the start of each retry iteration so that the
     * security provider is always invoked with the latest state (e.g. to refresh
     * an expired OAuth2 token).  If the supplier itself throws (pre-flight failure),
     * the exception is subject to the retry policy just like an HTTP-phase failure.
     *
     * @param method      The HTTP method (e.g. "GET", "POST").
     * @param uri         The fully-resolved request URI.
     * @param supplier    A supplier that builds the outbound request; called on each attempt.
     * @param bodyHandler The response body handler.
     * @return The successful HTTP response.
     * @throws RuntimeException if the request ultimately fails (after exhausting retries
     *                          or if the retry policy does not approve a retry).
     */
    <T> HttpResponse<T> send(String method,
                             URI uri,
                             Supplier<OutboundRequest> supplier,
                             HttpResponse.BodyHandler<T> bodyHandler) {
        int attempt = 1;

        while (true) {
            StopWatch watch = StopWatch.start();
            OutboundRequest outbound = null;
            RuntimeException failure;

            try {
                outbound = supplier.get();                                                  // pre-flight phase
                HttpResponse<T> response = executeRequest(outbound.request(), bodyHandler); // HTTP phase

                watch.stop();
                handleSuccess(outbound, response, bodyHandler, watch.duration(), attempt);
                return response;
            } catch (HttpResponseException hre) {
                watch.stop();
                failure = handleHttpError(outbound, hre, watch.duration(), attempt);
            } catch (RuntimeException ex) {
                watch.stop();
                failure = handleTransportError(outbound, ex, watch.duration(), attempt);
            }

            RetryContext context = buildRetryContext(
                    attempt,
                    failure,
                    method,
                    uri,
                    outbound
            );

            Optional<Duration> delay = retryPolicy.retryDelay(context);

            if (delay.isPresent()) {
                attempt = sleepBeforeRetry(attempt, delay.get(), method, uri);
                continue;
            }

            throw failure;
        }
    }

    private <T> void handleSuccess(OutboundRequest outbound,
                                   HttpResponse<T> response,
                                   HttpResponse.BodyHandler<T> bodyHandler,
                                   Duration latency,
                                   int attempt) {
        byte[] responseSnapshot = bodyHandler instanceof JsonBodyHandler<?> jbh
                ? jbh.snapshot()
                : null;

        requestLogger.logSuccess(outbound, response, latency, responseSnapshot, attempt);

        fireRequestCompleted(new RequestCompletedEvent(
                outbound.request().method(),
                outbound.request().uri(),
                response.statusCode(),
                latency
        ));
    }

    private RuntimeException handleHttpError(OutboundRequest outbound,
                                             HttpResponseException hre,
                                             Duration latency,
                                             int attempt) {
        // outbound is always non-null here: HttpResponseException is only thrown
        // by the HTTP phase (inside executeRequest), never by the auth phase.
        requestLogger.logError(outbound, hre, latency, attempt);

        fireRequestFailed(
                buildFailedEvent(
                        RequestFailedEvent.httpFailure(
                                outbound.request().method(),
                                outbound.request().uri(),
                                hre.statusCode(),
                                latency,
                                hre
                        ),
                        attempt
                )
        );

        return hre;
    }

    private RuntimeException handleTransportError(OutboundRequest outbound,
                                                  RuntimeException ex,
                                                  Duration latency,
                                                  int attempt) {
        // outbound is null when the auth phase threw before the request was built.
        // Only log and fire an event when we have a fully-formed request to report on.
        if (null == outbound) {
            requestLogger.logTransportError(ex, attempt);
        } else {
            requestLogger.logTransportError(outbound, ex, attempt);

            fireRequestFailed(
                    buildFailedEvent(
                            RequestFailedEvent.transportFailure(
                                    outbound.request().method(),
                                    outbound.request().uri(),
                                    latency,
                                    ex
                            ),
                            attempt
                    )
            );
        }

        return ex;
    }

    /**
     * Sleeps for {@code delay} and returns the next attempt number.
     * Throws {@link AbortedException} if the thread is interrupted during the sleep.
     */
    private int sleepBeforeRetry(int attempt, Duration delay, String method, URI uri) {
        try {
            TimeUnit.MILLISECONDS.sleep(delay.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AbortedException(ie, method, uri);
        }

        return attempt + 1;
    }

    /**
     * Sends an HTTP request asynchronously, rebuilding it via {@code supplier} on each
     * retry attempt so that the security provider is always called with the latest state.
     *
     * @param method      The HTTP method (e.g. "GET", "POST").
     * @param uri         The fully-resolved request URI.
     * @param supplier    A supplier that builds the outbound request; called on each attempt.
     * @param bodyHandler The response body handler.
     * @return A future that resolves to the successful HTTP response, or fails if the
     * request ultimately fails (after exhausting retries or if the retry policy
     * does not approve a retry).
     */
    <T> CompletableFuture<HttpResponse<T>> sendAsync(String method,
                                                     URI uri,
                                                     Supplier<OutboundRequest> supplier,
                                                     HttpResponse.BodyHandler<T> bodyHandler) {
        CompletableFuture<HttpResponse<T>> resultFuture = new CompletableFuture<>();

        retryAsync(
                method,
                uri,
                supplier,
                bodyHandler,
                1,
                resultFuture
        );

        return resultFuture;
    }

    private <T> void retryAsync(String method,
                                URI uri,
                                Supplier<OutboundRequest> supplier,
                                HttpResponse.BodyHandler<T> bodyHandler,
                                int attempt,
                                CompletableFuture<HttpResponse<T>> resultFuture) {
        OutboundRequest outbound;

        try {
            outbound = supplier.get();    // synchronous pre-flight — called on the current thread
        } catch (RuntimeException ex) {
            // Pre-flight failure: run through the retry policy.
            RetryContext context = buildRetryContext(attempt, ex, method, uri, null);

            Optional<Duration> retryDelay = retryPolicy.retryDelay(context);

            if (retryDelay.isEmpty()) {
                resultFuture.completeExceptionally(ex);
                return;
            }

            int nextAttempt = attempt + 1;

            DEFAULT_RETRY_SCHEDULER.schedule(
                    () -> retryAsync(method, uri, supplier, bodyHandler, nextAttempt, resultFuture),
                    retryDelay.get().toMillis(),
                    TimeUnit.MILLISECONDS
            );

            return;
        }

        final OutboundRequest finalOutbound = outbound;

        singleSendAsync(
                finalOutbound,
                bodyHandler,
                attempt
        ).whenComplete((response, throwable) -> {
            if (null == throwable) {
                resultFuture.complete(response);
                return;
            }

            Throwable unwrapped = unwrapCompletionException(throwable);

            RetryContext context = buildRetryContext(attempt, unwrapped, method, uri, finalOutbound);

            Optional<Duration> retryDelay = retryPolicy.retryDelay(context);

            if (retryDelay.isEmpty()) {
                resultFuture.completeExceptionally(throwable);
                return;
            }

            int nextAttempt = attempt + 1;

            DEFAULT_RETRY_SCHEDULER.schedule(
                    () -> retryAsync(method, uri, supplier, bodyHandler, nextAttempt, resultFuture),
                    retryDelay.get().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        });
    }

    /**
     * Single async attempt within a retry sequence — {@code attempt} is 1-based.
     */
    private <T> CompletableFuture<HttpResponse<T>> singleSendAsync(OutboundRequest outbound,
                                                                   HttpResponse.BodyHandler<T> bodyHandler,
                                                                   int retryAttempt) {
        StopWatch watch = StopWatch.start();
        HttpRequest request = outbound.request();

        return httpClient.sendAsync(request, bodyHandler)
                .handle((response, throwable) -> {
                    watch.stop();
                    Duration latency = watch.duration();

                    if (null == throwable) {
                        byte[] responseSnapshot = bodyHandler instanceof JsonBodyHandler<?> jbh
                                ? jbh.snapshot()
                                : null;

                        requestLogger.logSuccess(outbound, response, latency, responseSnapshot, retryAttempt);

                        fireRequestCompleted(new RequestCompletedEvent(
                                request.method(),
                                request.uri(),
                                response.statusCode(),
                                latency
                        ));

                        return response;
                    }

                    Throwable cause = unwrapCompletionException(throwable);

                    // Unwrap HttpResponseException the JDK wrapped in IOException (see send() above).
                    cause = unwrapHttpResponseException(cause);

                    if (cause instanceof HttpResponseException hre) {
                        requestLogger.logError(outbound, hre, latency, retryAttempt);

                        fireRequestFailed(
                                buildFailedEvent(
                                        RequestFailedEvent.httpFailure(request.method(), request.uri(), hre.statusCode(), latency, hre),
                                        retryAttempt
                                )
                        );

                        throw hre;
                    } else {
                        cause = wrapIfIOException(cause, request.method(), request.uri());

                        requestLogger.logTransportError(outbound, cause, retryAttempt);

                        fireRequestFailed(
                                buildFailedEvent(
                                        RequestFailedEvent.transportFailure(request.method(), request.uri(), latency, cause),
                                        retryAttempt
                                )
                        );

                        throw new CompletionException(cause);
                    }
                });
    }

    private RetryContext buildRetryContext(int attempt,
                                           Throwable failure,
                                           String method,
                                           URI uri,
                                           OutboundRequest outbound) {
        RetryPhase phase;
        OptionalInt statusCode;
        boolean replayableBody;

        if (null == outbound) {
            // Pre-flight failure — request was never sent
            phase = RetryPhase.PRE_FLIGHT;
            statusCode = OptionalInt.empty();
            replayableBody = true;  // moot since we can't know without the request
        } else if (failure instanceof HttpResponseException hre) {
            // HTTP-response failure
            phase = RetryPhase.HTTP_RESPONSE;
            statusCode = OptionalInt.of(hre.statusCode());
            replayableBody = outbound.bodySnapshot() != OutboundRequest.MULTIPART_SNAPSHOT;
        } else {
            // Transport failure
            phase = RetryPhase.TRANSPORT;
            statusCode = OptionalInt.empty();
            replayableBody = outbound.bodySnapshot() != OutboundRequest.MULTIPART_SNAPSHOT;
        }

        return new RetryContext(
                attempt,
                failure,
                method,
                uri,
                statusCode,
                replayableBody,
                phase
        );
    }

    private void fireRequestCompleted(RequestCompletedEvent event) {
        try {
            eventListener.onRequestCompleted(event);
        } catch (Exception ex) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "ClientEventListener.onRequestCompleted threw an unexpected exception",
                    ex
            );
        }
    }

    private void fireRequestFailed(RequestFailedEvent event) {
        try {
            eventListener.onRequestFailed(event);
        } catch (Exception ex) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "ClientEventListener.onRequestFailed threw an unexpected exception",
                    ex
            );
        }
    }
}
