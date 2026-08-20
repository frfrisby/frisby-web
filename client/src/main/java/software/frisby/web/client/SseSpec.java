package software.frisby.web.client;

import software.frisby.web.client.exception.UriSyntaxException;
import software.frisby.web.client.security.SecurityProvider;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A fluent builder for configuring and opening a Server-Sent Events (SSE) stream.
 * <p>
 * Obtained from {@link Client#sse()}.  Configure the request by chaining methods; the
 * request is not sent until a terminal method ({@link #stream()} or
 * {@link #streamAsync()}) is called.
 * <p>
 * {@code SseSpec} is deliberately minimal — it returns the raw response stream, exactly
 * like {@link GetSpec#download()}, with no dependency beyond the core {@code client}
 * module.  Callers who want typed per-event-type callback dispatch, automatic
 * reconnection, and backpressure handling should use the {@code software.frisby.web:client-sse}
 * module instead.  Callers who want to write their own SSE reader can use this type
 * directly with no additional dependency.
 * <p>
 * Resuming a stream after a drop is just another request — set the
 * {@link Headers#LAST_EVENT_ID} header with the last successfully processed event's
 * {@code id}:
 *
 * <pre>{@code
 * HttpResponse<InputStream> response = client.sse()
 *         .path("/notifications/stream")
 *         .header(Headers.LAST_EVENT_ID, lastReceivedId)
 *         .stream();
 * }</pre>
 *
 * @see Client#sse()
 */
public interface SseSpec {
    /**
     * Sets the URI path for this request.
     * <p>
     * The path is resolved against the base URI configured on the client.  Leading and
     * trailing slashes are normalized automatically.
     *
     * @param path The URI path relative to the client's base URI.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code path} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code path} is blank.
     */
    SseSpec path(String path);

    /**
     * Sets the URI path for this request, substituting a single named placeholder.
     * <p>
     * The placeholder must appear in the path surrounded by braces
     * (e.g. {@code {id}}).
     *
     * <pre>{@code
     * client.sse().path("/streams/{id}", "id", streamId)
     * }</pre>
     *
     * @param path           The URI path template containing the placeholder.
     * @param parameterId    The name of the placeholder to replace, without braces.
     * @param parameterValue The value to substitute for the placeholder.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code path}, {@code parameterId}, or
     *                                                             {@code parameterValue} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code path}, {@code parameterId}, or
     *                                                             {@code parameterValue} is blank.
     * @throws UriSyntaxException                                  if {@code parameterId} does not correspond to a
     *                                                             placeholder in {@code path}.
     */
    SseSpec path(String path, String parameterId, String parameterValue);

    /**
     * Sets the URI path for this request, substituting one or more named placeholders.
     *
     * <pre>{@code
     * client.sse().path(
     *         "/teams/{teamId}/members/{memberId}/stream",
     *         PathParameter.of("teamId", teamId),
     *         PathParameter.of("memberId", memberId)
     * )
     * }</pre>
     *
     * @param path       The URI path template containing the placeholders.
     * @param parameters The parameters whose names and values will be substituted.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code path} or {@code parameters} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code path} is blank.
     * @throws UriSyntaxException                                  if any parameter does not correspond to a
     *                                                             placeholder in {@code path}.
     */
    SseSpec path(String path, PathParameter... parameters);

    /**
     * Adds a query parameter to the request URI.
     *
     * @param name  The query parameter name.
     * @param value The query parameter value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     */
    SseSpec parameter(String name, String value);

    /**
     * Adds a multivalued query parameter to the request URI.  One {@code name=value}
     * pair is appended for each provided value.
     *
     * @param name   The query parameter name.
     * @param values The query parameter values; one entry is added per value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code name} is null or {@code values} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code values} is empty.
     * @throws software.frisby.core.validation.BlankValueException      if {@code name} is blank.
     */
    SseSpec parameter(String name, String... values);

    /**
     * Adds a request header.
     * <p>
     * The following headers are managed by the client and must not be set here:
     * {@link Headers#ACCEPT}, {@link Headers#ACCEPT_ENCODING},
     * {@link Headers#CONTENT_TYPE}, {@link Headers#CONTENT_LENGTH},
     * {@link Headers#CONTENT_ENCODING}, and {@link Headers#TRANSFER_ENCODING}.
     * Attempting to set any of these will throw an {@link IllegalArgumentException}.
     * <p>
     * Use {@link Headers#LAST_EVENT_ID} with this method to resume a stream after a
     * drop — there is no dedicated {@code lastEventId()} method.
     *
     * @param name  The header name.  See {@link Headers} for well-known header name constants.
     * @param value The header value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     * @throws IllegalArgumentException                            if {@code name} is a client-managed header.
     */
    SseSpec header(String name, String value);

    /**
     * Adds a multivalued request header.  One header entry is added per value.
     * <p>
     * The same restrictions on client-managed headers apply as for
     * {@link #header(String, String)}.
     *
     * @param name   The header name.  See {@link Headers} for well-known header name constants.
     * @param values The header values; one entry is added per value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code name} is null or {@code values} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code values} is empty.
     * @throws software.frisby.core.validation.BlankValueException      if {@code name} is blank.
     * @throws IllegalArgumentException                                 if {@code name} is a client-managed header.
     */
    SseSpec header(String name, String... values);

    /**
     * Adds a cookie to the request.
     *
     * @param cookie The cookie to include.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code cookie} is null.
     */
    SseSpec cookie(HttpCookie cookie);

    /**
     * Sets the security provider that will add authentication credentials to this request.
     * <p>
     * This overrides any default security provider configured on the client for this
     * individual request.
     *
     * @param provider The security provider to apply.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code provider} is null.
     */
    SseSpec security(SecurityProvider provider);

    /**
     * Sends the request and returns the response body as a raw {@link InputStream} of
     * {@code text/event-stream} bytes.
     * <p>
     * Automatically sets {@code Accept: text/event-stream} unless the caller already set
     * an {@code Accept} header.  The caller is responsible for parsing the SSE wire
     * format and for closing the returned stream.
     *
     * @return The HTTP response whose body is a stream of the raw response bytes.
     */
    HttpResponse<InputStream> stream();

    /**
     * Sends the request asynchronously and returns the response body as a raw
     * {@link InputStream} of {@code text/event-stream} bytes.
     * <p>
     * The caller is responsible for closing the stream when the future completes.
     *
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     */
    CompletableFuture<HttpResponse<InputStream>> streamAsync();
}

