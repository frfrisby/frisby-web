package software.frisby.web.server;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.server.internal.process.MappableException;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import software.frisby.core.util.StopWatch;
import software.frisby.core.validation.Values;
import software.frisby.web.server.event.RequestCompletedEvent;
import software.frisby.web.server.event.ServerEventListener;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Jersey {@link RequestEventListener} that handles per-request logging and
 * {@link ServerEventListener} callbacks.
 * <p>
 * Instantiated by {@code ServerApplicationEventListener} for every inbound request.
 * Fires at {@link RequestEvent.Type#FINISHED} to emit a single combined log entry
 * covering the full request/response exchange and to notify the configured
 * {@link ServerEventListener}.
 * <p>
 * The {@code buildDetail} method assembles the multi-line detail block used by
 * {@code TRACE}-level success entries and {@code WARNING}/{@code ERROR} failure
 * entries.  Its logic is split into {@link #appendRequestBodySection} and
 * {@link #appendResponseBodySection} to keep cognitive complexity manageable.
 */
final class ServerRequestEventListener implements RequestEventListener {
    private static final System.Logger LOGGER = System.getLogger(ServerRequestEventListener.class.getName());

    private static final String INDENT_1 = "\n  ";
    private static final String INDENT_2 = "\n    ";

    /**
     * Request context property key under which {@code RequestBodyBufferingFilter} stores
     * the first {@code maxLogBodySize} bytes of the request body as a {@code byte[]}.
     * Read by {@link #appendRequestBodySection} when building detail blocks.
     */
    static final String BUFFERED_BODY_KEY =
            "software.frisby.web.server.ServerRequestEventListener.requestBody";

    /**
     * Returns {@code true} for content types that are safe to buffer and display as
     * UTF-8 text in failure logs.
     * <p>
     * Whitelisted families:
     * <ul>
     *   <li>{@code text/*} — any text type</li>
     *   <li>{@code application/json}, {@code application/*+json}</li>
     *   <li>{@code application/xml}, {@code application/*+xml}</li>
     *   <li>{@code application/x-www-form-urlencoded}</li>
     *   <li>{@code application/graphql}</li>
     * </ul>
     * Everything else — {@code application/octet-stream}, {@code image/*},
     * {@code audio/*}, {@code video/*}, etc. — is treated as binary and not buffered.
     * A {@code null} media type returns {@code true} so that requests without a
     * {@code Content-Type} header are buffered optimistically (they typically have no
     * body and the buffer remains empty).
     */
    static boolean isTextBody(MediaType mediaType) {
        if (null == mediaType) {
            return true;
        }

        String type = mediaType.getType().toLowerCase(Locale.ROOT);
        String subtype = mediaType.getSubtype().toLowerCase(Locale.ROOT);

        if ("text".equals(type)) {
            return true;
        }

        if ("application".equals(type)) {
            return "json".equals(subtype)
                    || subtype.endsWith("+json")
                    || "xml".equals(subtype)
                    || subtype.endsWith("+xml")
                    || "x-www-form-urlencoded".equals(subtype)
                    || "graphql".equals(subtype);
        }

        return false;
    }

    private final ServerConfiguration configuration;
    private final ServerEventListener eventListener;
    private final RequestLogger requestLogger;
    private final String healthCheckPath;
    private final StopWatch watch;

    // Captured at ON_EXCEPTION — the FINISHED event clears its exception field once
    // an ExceptionMapper has successfully produced a response, so we must preserve
    // the original throwable here for inclusion in the 5xx failure log.
    private Throwable requestException;

    ServerRequestEventListener(ServerConfiguration configuration,
                               ServerEventListener eventListener,
                               RequestLogger requestLogger,
                               String healthCheckPath) {
        this.configuration = configuration;
        this.eventListener = eventListener;
        this.requestLogger = requestLogger;
        this.healthCheckPath = healthCheckPath;
        this.watch = StopWatch.start();
        this.requestException = null;
    }

    /**
     * Unwraps Jersey's internal {@link MappableException} to expose the original exception
     * thrown by application code.
     * <p>
     * When a resource method throws any exception, Jersey wraps it in a
     * {@link MappableException} before the exception-mapper lookup phase.  That wrapper
     * is what appears on the {@code ON_EXCEPTION} event.  Unwrapping it here means the
     * original application exception — with its real type and message — is what gets
     * attached to the log record.
     *
     * @return The cause of {@code cause} if it is a {@link MappableException}; otherwise
     * {@code cause} unchanged.
     */
    private static Throwable unwrapJerseyException(Throwable cause) {
        if (null == cause) {
            return null;
        }

        if (cause instanceof MappableException) {
            return cause.getCause();
        }

        return cause;
    }

    /**
     * Converts a response entity to a loggable string.
     * <ul>
     *   <li>{@code String} entities are returned as-is.</li>
     *   <li>{@code InputStream} entities return {@code null} — they are binary or streaming
     *       and cannot be safely re-read after the response has been written.</li>
     *   <li>All other entities are serialized via {@code serializer}; if serialization
     *       fails the entity's simple class name is returned in brackets.</li>
     * </ul>
     *
     * @return A loggable string, or {@code null} if the entity should not be logged.
     */
    private static String serializeEntityForLog(Object entity, software.frisby.web.serial.JsonSerializer serializer) {
        if (null == entity) {
            return null;
        }

        if (entity instanceof String s) {
            return s;
        }

        if (entity instanceof InputStream) {
            // Binary or streaming — cannot be re-read; skip silently.
            return null;
        }

        try {
            return new String(serializer.serialize(entity), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "[" + entity.getClass().getSimpleName() + "]";
        }
    }

    private static String formatRequestHeaders(MultivaluedMap<String, String> headers,
                                               Set<String> masked) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            String lowerName = name.toLowerCase(Locale.ROOT);

            if (masked.contains(lowerName) && "cookie".equals(lowerName)) {
                // Each Cookie header value may contain multiple cookies separated by ';'.
                // Iterate the value list and delegate to LogDetail for per-cookie handling.
                for (String cookieValue : entry.getValue()) {
                    LogDetail.appendRequestHeader(sb, name, cookieValue, masked);
                }
            } else {
                LogDetail.appendRequestHeader(sb, name, String.join(", ", entry.getValue()), masked);
            }
        }

        return sb.toString();
    }

    private static String formatResponseHeaders(MultivaluedMap<String, Object> headers,
                                                Set<String> masked) {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, List<Object>> entry : headers.entrySet()) {
            String name = entry.getKey();
            String lowerName = name.toLowerCase(Locale.ROOT);

            if (masked.contains(lowerName) && "set-cookie".equals(lowerName)) {
                // Each Set-Cookie value is an independent cookie — render one line per
                // value so attributes remain readable, with the value redacted.
                for (Object cookieValue : entry.getValue()) {
                    sb.append(INDENT_2).append(name).append(": ")
                            .append(LogDetail.redactSetCookieHeader(cookieValue.toString()));
                }
            } else {
                String value = masked.contains(lowerName)
                        ? "[redacted]"
                        : entry.getValue().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));

                sb.append(INDENT_2).append(name).append(": ").append(value);
            }
        }

        return sb.toString();
    }

    /**
     * Builds a multi-line detail string for log entries, including masked
     * request headers, optionally redacted request body, response headers,
     * and optionally redacted response body.
     * <p>
     * Used for both {@code TRACE}-level success entries and {@code WARNING}/{@code ERROR}
     * failure entries.
     *
     * @param request  The JAX-RS container request context.
     * @param response The JAX-RS container response context.
     * @param config   The server configuration carrying masking/redaction settings.
     * @return A multi-line detail string (starts with {@code \n}); never {@code null}.
     */
    private static String buildDetail(ContainerRequestContext request,
                                      ContainerResponseContext response,
                                      ServerConfiguration config) {
        StringBuilder sb = new StringBuilder();

        Set<String> maskedHeaders = config.logging().redactedHeaders();
        Set<String> redactedFields = config.logging().redactedBodyFields();
        int maxBodySize = config.logging().maxBodySize();

        sb.append(INDENT_1).append("Request Headers:").append(formatRequestHeaders(
                request.getHeaders(),
                maskedHeaders
        ));

        appendRequestBodySection(sb, request, maxBodySize, redactedFields);

        MultivaluedMap<String, Object> responseHeaders = response.getHeaders();

        if (!responseHeaders.isEmpty()) {
            sb.append(INDENT_1).append("Response Headers:").append(formatResponseHeaders(
                    responseHeaders,
                    maskedHeaders
            ));
        }

        appendResponseBodySection(sb, response, maxBodySize, redactedFields, config.serializer());

        return sb.toString();
    }

    /**
     * Appends the request body section to {@code sb} when body logging is enabled.
     * <p>
     * Multipart and binary bodies are not buffered; a placeholder is rendered instead.
     * Text-based bodies are buffered by {@code RequestBodyBufferingFilter} up to
     * {@code maxBodySize} bytes and may be redacted per {@code redactedFields}.
     */
    private static void appendRequestBodySection(StringBuilder sb,
                                                 ContainerRequestContext request,
                                                 int maxBodySize,
                                                 Set<String> redactedFields) {
        if (0 >= maxBodySize) {
            return;
        }

        MediaType mediaType = request.getMediaType();
        boolean isMultipart = null != mediaType && "multipart".equals(mediaType.getType());
        boolean isBinaryBody = null != mediaType && !isMultipart && !isTextBody(mediaType);
        boolean isFormEncoded = null != mediaType
                && MediaType.APPLICATION_FORM_URLENCODED_TYPE.isCompatible(mediaType);

        if (isMultipart || isBinaryBody) {
            sb.append(INDENT_1).append("Request Body:").append(INDENT_2).append("[")
                    .append(mediaType.getType()).append("/").append(mediaType.getSubtype())
                    .append(" — body not logged]");
            return;
        }

        byte[] bufferedBody = (byte[]) request.getProperty(BUFFERED_BODY_KEY);

        if (null == bufferedBody) {
            return;
        }

        String body = new String(bufferedBody, StandardCharsets.UTF_8);

        if (!redactedFields.isEmpty()) {
            body = isFormEncoded
                    ? LogDetail.redactFormValues(body, redactedFields)
                    : LogDetail.redactFieldValues(body, redactedFields);
        }

        sb.append(INDENT_1).append("Request Body:").append(INDENT_2).append(body);

        if (bufferedBody.length >= maxBodySize) {
            sb.append(" [truncated at ").append(maxBodySize).append(" bytes]");
        }
    }

    /**
     * Appends the response body section to {@code sb} when body logging is enabled.
     * <p>
     * The entity is serialized via {@code serializer}; {@code InputStream} entities
     * and serialization failures are handled gracefully and produce no output or a
     * bracketed class name respectively.  The body is redacted per {@code redactedFields}
     * and truncated to {@code maxBodySize} characters.
     */
    private static void appendResponseBodySection(StringBuilder sb,
                                                  ContainerResponseContext response,
                                                  int maxBodySize,
                                                  Set<String> redactedFields,
                                                  software.frisby.web.serial.JsonSerializer serializer) {
        if (0 >= maxBodySize) {
            return;
        }

        String responseBody = serializeEntityForLog(response.getEntity(), serializer);

        if (null == responseBody || responseBody.isBlank()) {
            return;
        }

        if (!redactedFields.isEmpty()) {
            responseBody = LogDetail.redactFieldValues(responseBody, redactedFields);
        }

        if (responseBody.length() > maxBodySize) {
            responseBody = responseBody.substring(0, maxBodySize)
                    + " [truncated at " + maxBodySize + " bytes]";
        }

        sb.append(INDENT_1).append("Response Body:").append(INDENT_2).append(responseBody);
    }

    @Override
    public void onEvent(RequestEvent event) {
        // Capture the exception as early as possible.  Jersey clears getException()
        // on the FINISHED event once an ExceptionMapper has produced a response, so
        // we record it here before the mapping phase runs.
        //
        // Jersey wraps resource-method exceptions in an internal MappableException;
        // unwrap it so that the original application exception appears in the log.
        if (event.getType() == RequestEvent.Type.ON_EXCEPTION) {
            requestException = unwrapJerseyException(event.getException());
        }

        if (event.getType() != RequestEvent.Type.FINISHED) {
            return;
        }

        watch.stop();

        Duration latency = watch.duration();
        String method = event.getContainerRequest().getMethod();
        String path = event.getContainerRequest().getUriInfo().getRequestUri().getPath();
        long requestBytes = Math.max(0L, event.getContainerRequest().getLength());

        boolean isHealthCheck = path.equals(healthCheckPath);

        ContainerResponseContext containerResponse = Values.notNull(
                "containerResponse",
                event.getContainerResponse()
        );

        int statusCode = containerResponse.getStatus();
        long responseBytes = Math.max(0L, containerResponse.getLength());

        if (isHealthCheck) {
            requestLogger.logHealthCheck(method, path, statusCode, latency.toMillis());
        } else {
            RequestCompletedEvent requestCompletedEvent = new RequestCompletedEvent(
                    method,
                    path,
                    statusCode,
                    latency,
                    requestBytes,
                    responseBytes
            );

            String detail = "";

            if (statusCode < 400) {
                // 2xx / 3xx — full detail at TRACE; one-liner at INFO.
                if (requestLogger.isTraceLoggable()) {
                    detail = buildDetail(
                            event.getContainerRequest(),
                            containerResponse,
                            configuration
                    );
                }

                requestLogger.logRequest(method, path, statusCode, latency.toMillis(), detail);
            } else {
                // 4xx → WARNING; 5xx + WAE → WARNING; 5xx + uncaught exception → ERROR.
                // Include request context detail only when the corresponding log level
                // is actually enabled.
                if (requestLogger.isDetailLoggable(statusCode, requestException)) {
                    detail = buildDetail(
                            event.getContainerRequest(),
                            containerResponse,
                            configuration
                    );
                }

                requestLogger.logFailureDetail(
                        method,
                        path,
                        statusCode,
                        latency.toMillis(),
                        detail,
                        requestException
                );
            }

            fireRequestCompleted(requestCompletedEvent);
        }
    }

    private void fireRequestCompleted(RequestCompletedEvent event) {
        try {
            eventListener.onRequestCompleted(event);
        } catch (Exception ex) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "ServerEventListener.onRequestCompleted threw an exception.",
                        ex
                );
            }
        }
    }
}

