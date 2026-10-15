package software.frisby.web.client;

import software.frisby.core.validation.Durations;
import software.frisby.web.client.security.SecurityProvider;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Shared composition helper for every stream-producing spec ({@link DownloadSpec},
 * {@link SseSpec}) — plain composition, the same pattern {@link RequestState} itself
 * follows for the non-streaming verb specs.
 * <p>
 * Owns navigation delegation (via its own {@link RequestState}), the per-call {@code
 * idleTimeout} override, request building, error mapping, and wrapping the response
 * {@link InputStream} in a {@link WatchdogTrackingInputStream} — all in one place,
 * rather than duplicated between {@link DownloadRequest} and {@code SseRequest}.
 * <p>
 * Navigation methods here are {@code void}-returning rather than fluent-returning-
 * {@code this} — fluent chaining stays on the owning spec implementation class (each
 * returns its own concrete spec type), avoiding any CRTP/generic-self-type complexity
 * in this shared helper itself.
 * <p>
 * {@code idleTimeout} feeds <strong>both</strong> the pre-first-byte JDK timeout
 * ({@code HttpRequest.Builder#timeout(Duration)}, applied by {@link RequestState
 * #prepareBuilder}) and the post-first-byte {@link StreamWatchdog} tracking, applied by
 * wrapping the successful response body in a {@link WatchdogTrackingInputStream}
 * constructed with that same value — one number for "how long am I willing to wait for
 * the next thing to happen," per the design document this chunk implements.
 *
 * @see DownloadRequest
 */
final class StreamRequestSupport {
    private static final String GET = "GET";
    private static final String TIMEOUT_ARGUMENT_NAME = "timeout";

    private final HttpEngine engine;
    private final RequestState state;
    private final String defaultAcceptHeaderValue;
    private Duration idleTimeout;

    /**
     * @param engine                   The shared {@link HttpEngine} used to dispatch the request.
     * @param defaultSecurity          The client's default {@link SecurityProvider}, if any.
     * @param defaultAcceptHeaderValue The {@code Accept} header value to apply when the
     *                                 caller has not already set one — {@code null} for
     *                                 {@link DownloadRequest} (no default), {@code
     *                                 "text/event-stream"} for {@code SseRequest}.
     */
    StreamRequestSupport(HttpEngine engine, SecurityProvider defaultSecurity, String defaultAcceptHeaderValue) {
        this.engine = engine;
        this.state = new RequestState(defaultSecurity);
        this.defaultAcceptHeaderValue = defaultAcceptHeaderValue;
        this.idleTimeout = null;
    }

    void path(String path) {
        state.path(path);
    }

    void path(String path, String parameterId, String parameterValue) {
        state.path(path, parameterId, parameterValue);
    }

    void path(String path, PathParameter... parameters) {
        state.path(path, parameters);
    }

    void parameter(String name, String value) {
        state.parameter(name, value);
    }

    void parameter(String name, String... values) {
        state.parameter(name, values);
    }

    void header(String name, String value) {
        state.header(name, value);
    }

    void header(String name, String... values) {
        state.header(name, values);
    }

    void cookie(HttpCookie cookie) {
        state.cookie(cookie);
    }

    void security(SecurityProvider provider) {
        state.security(provider);
    }

    void idleTimeout(Duration timeout) {
        this.idleTimeout = Durations.positive(TIMEOUT_ARGUMENT_NAME, timeout);
    }

    /**
     * Sends the request synchronously and returns the response body as a watchdog-
     * tracked {@link InputStream}.
     *
     * @return The HTTP response whose body is a watchdog-tracked stream of the raw
     * response bytes.
     */
    HttpResponse<InputStream> execute() {
        URI uri = state.resolveUri(engine.configuration().uri());
        Duration effectiveIdleTimeout = effectiveIdleTimeout();

        return engine.send(
                GET,
                uri,
                () -> OutboundRequest.of(buildRequest(uri, effectiveIdleTimeout)),
                bodyHandler(uri, effectiveIdleTimeout)
        );
    }

    /**
     * Sends the request asynchronously and returns the response body as a
     * watchdog-tracked {@link InputStream}.
     *
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     */
    CompletableFuture<HttpResponse<InputStream>> executeAsync() {
        URI uri = state.resolveUri(engine.configuration().uri());
        Duration effectiveIdleTimeout = effectiveIdleTimeout();

        return engine.sendAsync(
                GET,
                uri,
                () -> OutboundRequest.of(buildRequest(uri, effectiveIdleTimeout)),
                bodyHandler(uri, effectiveIdleTimeout)
        );
    }

    /**
     * Resolves the idle timeout to apply for this call: the per-call override if set,
     * otherwise {@link ClientConfiguration#idleTimeout()}.
     */
    private Duration effectiveIdleTimeout() {
        return null != idleTimeout ? idleTimeout : engine.configuration().idleTimeout();
    }

    private HttpRequest buildRequest(URI uri, Duration effectiveIdleTimeout) {
        HttpRequest.Builder builder = state.prepareBuilder(
                uri,
                GET,
                HttpRequest.BodyPublishers.noBody(),
                false,
                null,
                effectiveIdleTimeout
        );

        if (null != defaultAcceptHeaderValue && !state.hasHeader(Headers.ACCEPT)) {
            builder.header(Headers.ACCEPT, defaultAcceptHeaderValue);
        }

        return builder.build();
    }

    private HttpResponse.BodyHandler<InputStream> bodyHandler(URI uri, Duration effectiveIdleTimeout) {
        return responseInfo -> {
            if (ExceptionFactory.isError(responseInfo.statusCode())) {
                return HttpResponse.BodySubscribers.mapping(
                        HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                        (Function<String, InputStream>) body -> {
                            String errorBody = body.isBlank() ? null : body;

                            throw ExceptionFactory.create(
                                    errorBody,
                                    GET,
                                    uri,
                                    responseInfo.statusCode(),
                                    responseInfo.headers()
                            );
                        }
                );
            }

            return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofInputStream(),
                    (Function<InputStream, InputStream>) rawStream ->
                            new WatchdogTrackingInputStream(rawStream, effectiveIdleTimeout, uri)
            );
        };
    }
}
