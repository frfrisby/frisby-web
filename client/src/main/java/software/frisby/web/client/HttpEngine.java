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
import java.util.concurrent.*;

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

    private final HttpClient httpClient;
    private final ClientConfiguration configuration;
    private final ClientEventListener eventListener;
    private final RequestLogger requestLogger;

    HttpEngine(ClientConfiguration configuration, ClientEventListener eventListener) {
        this.configuration = Values.notNull("configuration", configuration);
        this.eventListener = Values.notNull("eventListener", eventListener);
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
     * Returns the {@link ClientConfiguration} used to create this engine.
     *
     * @return The client configuration.
     */
    ClientConfiguration configuration() {
        return configuration;
    }

    <T> HttpResponse<T> send(OutboundRequest outbound, HttpResponse.BodyHandler<T> bodyHandler) {
        StopWatch watch = StopWatch.start();
        HttpRequest request = outbound.request();

        try {
            HttpResponse<T> response = httpClient.send(request, bodyHandler);

            watch.stop();
            Duration latency = watch.duration();

            byte[] responseSnapshot = bodyHandler instanceof JsonBodyHandler<?> jbh
                    ? jbh.snapshot()
                    : null;

            requestLogger.logSuccess(outbound, response, latency, responseSnapshot);

            fireRequestCompleted(new RequestCompletedEvent(
                    request.method(),
                    request.uri(),
                    response.statusCode(),
                    latency
            ));

            return response;
        } catch (HttpConnectTimeoutException ex) {
            watch.stop();
            Duration latency = watch.duration();
            ConnectTimeoutException wrapped = new ConnectTimeoutException(
                    ex,
                    request.method(),
                    request.uri()
            );

            requestLogger.logTransportError(outbound, wrapped);
            fireRequestFailed(RequestFailedEvent.transportFailure(
                    request.method(), request.uri(), latency, wrapped
            ));

            throw wrapped;
        } catch (HttpTimeoutException ex) {
            watch.stop();
            Duration latency = watch.duration();
            ReadTimeoutException wrapped = new ReadTimeoutException(
                    ex,
                    request.method(),
                    request.uri()
            );

            requestLogger.logTransportError(outbound, wrapped);
            fireRequestFailed(RequestFailedEvent.transportFailure(
                    request.method(), request.uri(), latency, wrapped
            ));

            throw wrapped;
        } catch (java.net.ConnectException ex) {
            watch.stop();
            Duration latency = watch.duration();
            software.frisby.web.client.exception.ConnectException wrapped =
                    new software.frisby.web.client.exception.ConnectException(
                            ex,
                            request.method(),
                            request.uri()
                    );

            requestLogger.logTransportError(outbound, wrapped);
            fireRequestFailed(RequestFailedEvent.transportFailure(
                    request.method(), request.uri(), latency, wrapped
            ));

            throw wrapped;
        } catch (IOException ex) {
            watch.stop();
            Duration latency = watch.duration();

            // The JDK HttpClient wraps RuntimeExceptions thrown from body handler mapping
            // functions in a plain IOException (see HttpClientImpl.send() catch for
            // ExecutionException).  Unwrap HttpResponseException so callers always receive
            // the correctly-typed HTTP error exception instead of AbortedException.
            if (ex.getCause() instanceof HttpResponseException hre) {
                requestLogger.logError(outbound, hre, latency);

                fireRequestFailed(RequestFailedEvent.httpFailure(
                        request.method(),
                        request.uri(),
                        hre.statusCode(),
                        latency,
                        hre
                ));

                throw hre;
            }

            TransportException wrapped = new TransportException(ex, request.method(), request.uri());

            requestLogger.logTransportError(outbound, wrapped);
            fireRequestFailed(RequestFailedEvent.transportFailure(
                    request.method(), request.uri(), latency, wrapped
            ));

            throw wrapped;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            watch.stop();
            Duration latency = watch.duration();
            AbortedException wrapped = new AbortedException(ex, request.method(), request.uri());

            requestLogger.logTransportError(outbound, wrapped);
            fireRequestFailed(RequestFailedEvent.transportFailure(
                    request.method(), request.uri(), latency, wrapped
            ));

            throw wrapped;
        }
    }

    <T> CompletableFuture<HttpResponse<T>> sendAsync(OutboundRequest outbound,
                                                     HttpResponse.BodyHandler<T> bodyHandler) {
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

                        requestLogger.logSuccess(outbound, response, latency, responseSnapshot);

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
                        requestLogger.logError(outbound, hre, latency);

                        fireRequestFailed(RequestFailedEvent.httpFailure(
                                request.method(),
                                request.uri(),
                                hre.statusCode(),
                                latency,
                                hre
                        ));

                        throw hre;
                    } else {
                        cause = wrapIfIOException(cause, request.method(), request.uri());

                        requestLogger.logTransportError(outbound, cause);

                        fireRequestFailed(RequestFailedEvent.transportFailure(
                                request.method(),
                                request.uri(),
                                latency,
                                cause
                        ));

                        throw new CompletionException(cause);
                    }
                });
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
