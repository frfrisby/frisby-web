package software.frisby.web.server;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import software.frisby.web.server.event.RequestCompletedEvent;
import software.frisby.web.server.event.ServerEventListener;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * Replaces Jetty's default HTML error page with a JSON response for errors that
 * occur before a request reaches Jersey — primarily HTTP 413 from
 * {@link org.eclipse.jetty.server.handler.SizeLimitHandler}.
 * <p>
 * Because these errors are handled at the Jetty layer, Jersey's request event
 * pipeline does not run.  This handler fills the gap by:
 * <ul>
 *   <li>Returning {@code application/json} instead of Jetty's default
 *       {@code text/html}, keeping the API contract consistent for all callers.</li>
 *   <li>Logging at {@code WARNING} via {@link RequestLogger} — the same format as
 *       Jersey-level failures, including request headers (with masking applied) and a
 *       body-rejection note that states the payload size relative to the configured limit.
 *       (Jetty-layer errors carry no application exception, so they always resolve to
 *       {@code WARNING} regardless of the 4xx/5xx status code.)</li>
 *   <li>Firing {@link ServerEventListener#onRequestCompleted} so metrics backends
 *       receive Jetty-level rejections alongside all other responses.</li>
 * </ul>
 * <p>
 * Response body format:
 * <pre>{@code {"status":413,"message":"Request Entity Too Large"}}</pre>
 * <p>
 * Registered automatically by {@link DefaultServer}.
 */
final class JsonErrorHandler extends ErrorHandler {
    private static final System.Logger LOGGER = System.getLogger(JsonErrorHandler.class.getName());
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    private final ServerConfiguration configuration;
    private final RequestLogger requestLogger;
    private final ServerEventListener eventListener;

    JsonErrorHandler(ServerConfiguration configuration,
                     RequestLogger requestLogger,
                     ServerEventListener eventListener) {
        this.configuration = configuration;
        this.requestLogger = requestLogger;
        this.eventListener = eventListener;
    }

    @Override
    protected void generateResponse(
            Request request,
            Response response,
            int status,
            String message,
            Throwable cause,
            Callback callback) {
        long latencyMs = Math.max(0L, (System.nanoTime() - request.getBeginNanoTime()) / 1_000_000L);
        String method = request.getMethod();
        String path = request.getHttpURI().getPath();

        // Use the standard HTTP reason phrase, falling back to whatever Jetty provided.
        String reasonPhrase = HttpStatus.getMessage(status);

        String json = "{\"status\":" + status + ",\"message\":\"" + reasonPhrase + "\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        // Build the detail block only when the corresponding log level is enabled —
        // same style as DefaultServer.buildDetail() but sourced from Jetty's HttpFields.
        // Jetty-level errors have no application exception; pass null so determineFailureLevel
        // falls back to WARNING.
        String detail = requestLogger.isDetailLoggable(status, null)
                ? buildDetail(request, json)
                : "";

        // Log — same level convention as Jersey-level failures.  Jetty-layer errors have no
        // application exception; null cause maps to WARNING for all status codes.
        requestLogger.logFailureDetail(method, path, status, latencyMs, detail, null);

        // Fire event so that metrics backends see Jetty-level rejections alongside all other
        // responses.  Content-Length may be absent for chunked transfers; report 0 in that case.
        long requestBytes = 0L;
        try {
            requestBytes = Math.max(0L, request.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH));
        } catch (NumberFormatException ignored) {
        }

        try {
            eventListener.onRequestCompleted(new RequestCompletedEvent(
                    method,
                    path,
                    status,
                    Duration.ofMillis(latencyMs),
                    requestBytes,
                    jsonBytes.length
            ));
        } catch (Exception ex) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "ServerEventListener.onRequestCompleted threw an exception.",
                        ex
                );
            }
        }

        // Write the JSON body.
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, CONTENT_TYPE_JSON);
        response.getHeaders().put(HttpHeader.CONTENT_LENGTH, jsonBytes.length);
        response.write(true, ByteBuffer.wrap(jsonBytes), callback);
    }

    private String buildDetail(Request request, String responseJson) {
        StringBuilder sb = new StringBuilder();

        Set<String> maskedHeaders = configuration.logging().redactedHeaders();

        // Format request headers using the shared LogDetail helper — same masking
        // and cookie-name-preserving rules as DefaultServer.formatRequestHeaders.
        StringBuilder headersSb = new StringBuilder();

        for (HttpField field : request.getHeaders()) {
            if (headersSb.isEmpty()) {
                headersSb.append("\n  Request Headers:");
            }

            LogDetail.appendRequestHeader(headersSb, field.getName(), field.getValue(), maskedHeaders);
        }

        sb.append(headersSb);

        // The request body was rejected by SizeLimitHandler before it could be read —
        // there is nothing to buffer or redact.  Reporting the Content-Length lets the
        // operator see how large the rejected payload was relative to the configured limit.
        long contentLength = -1L;
        try {
            contentLength = request.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH);
        } catch (NumberFormatException ignored) {
        }

        // SizeLimitHandler only calls JsonErrorHandler for requests with a known
        // Content-Length (it rejects before Jersey when the header is present).
        // Chunked-transfer requests (no Content-Length) are handled by
        // BadMessageExceptionMapper inside Jersey, so this path always has contentLength > 0.
        sb.append("\n  Request Body:\n    [rejected — ")
                .append(contentLength)
                .append(" bytes exceeds server limit of ")
                .append(configuration.maxRequestSize())
                .append(" bytes]");

        sb.append("\n  Response Body:\n    ").append(responseJson);

        return sb.toString();
    }
}
