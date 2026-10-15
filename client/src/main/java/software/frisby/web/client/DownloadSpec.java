package software.frisby.web.client;

import software.frisby.web.client.exception.UriSyntaxException;
import software.frisby.web.client.security.SecurityProvider;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * A fluent builder for configuring and executing a raw file/binary download.
 * <p>
 * Obtained from {@link Client#download()}.  Configure the request by chaining methods;
 * the request is not sent until a terminal method ({@link #stream()} or
 * {@link #streamAsync()}) is called.
 * <p>
 * Split off of {@link GetSpec} specifically so {@link #idleTimeout(Duration)} can never
 * be a silent no-op: every terminal method on this spec is stream-producing, unlike
 * {@link GetSpec}, which is shared by both the streaming {@code download()}/
 * {@code downloadAsync()} methods it used to expose and the fully-buffered
 * {@code send(...)}/{@code sendAsync(...)} methods it still does — the same reasoning
 * that already motivated splitting {@link SseSpec} off in the same way.
 *
 * <pre>{@code
 * // File download
 * HttpResponse<InputStream> file = client.download()
 *         .path("/documents/{id}", "id", docId)
 *         .stream();
 *
 * // Non-blocking — fire and move on
 * client.download()
 *         .path("/documents/{id}", "id", docId)
 *         .streamAsync()
 *         .thenAccept(response -> process(response.body()));
 * }</pre>
 *
 * @see Client#download()
 */
public interface DownloadSpec {
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
    DownloadSpec path(String path);

    /**
     * Sets the URI path for this request, substituting a single named placeholder.
     * <p>
     * The placeholder must appear in the path surrounded by braces
     * (e.g. {@code {id}}).
     *
     * <pre>{@code
     * client.download().path("/documents/{id}", "id", docId)
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
    DownloadSpec path(String path, String parameterId, String parameterValue);

    /**
     * Sets the URI path for this request, substituting one or more named placeholders.
     *
     * <pre>{@code
     * client.download().path(
     *         "/teams/{teamId}/documents/{docId}",
     *         PathParameter.of("teamId", teamId),
     *         PathParameter.of("docId", docId)
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
    DownloadSpec path(String path, PathParameter... parameters);

    /**
     * Adds a query parameter to the request URI.
     *
     * @param name  The query parameter name.
     * @param value The query parameter value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     */
    DownloadSpec parameter(String name, String value);

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
    DownloadSpec parameter(String name, String... values);

    /**
     * Adds a request header.
     * <p>
     * The following headers are managed by the client and must not be set here:
     * {@link Headers#ACCEPT}, {@link Headers#ACCEPT_ENCODING},
     * {@link Headers#CONTENT_TYPE}, {@link Headers#CONTENT_LENGTH},
     * {@link Headers#CONTENT_ENCODING}, and {@link Headers#TRANSFER_ENCODING}.
     * Attempting to set any of these will throw an {@link IllegalArgumentException}.
     *
     * @param name  The header name.  See {@link Headers} for well-known header name constants.
     * @param value The header value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     * @throws IllegalArgumentException                            if {@code name} is a client-managed header.
     */
    DownloadSpec header(String name, String value);

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
    DownloadSpec header(String name, String... values);

    /**
     * Adds a cookie to the request.
     *
     * @param cookie The cookie to include.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code cookie} is null.
     */
    DownloadSpec cookie(HttpCookie cookie);

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
    DownloadSpec security(SecurityProvider provider);

    /**
     * Overrides, for this call only, the idle timeout used to bound both the
     * time-to-first-byte wait and any subsequent post-first-byte stall on the
     * downloaded stream.
     * <p>
     * See {@link ClientConfiguration#idleTimeout()} for the full explanation of what
     * {@code idleTimeout} bounds. When this method is never called, the connection-wide
     * {@link ClientConfiguration#idleTimeout()} default applies instead. Useful when a
     * single slow or bulky download endpoint shouldn't force loosening the default for
     * every other call made through the same {@link Client}.
     *
     * @param timeout The idle timeout to apply to this download; must not be {@code
     *                null} or negative.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException            if {@code timeout} is null.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code timeout} is not positive.
     */
    DownloadSpec idleTimeout(Duration timeout);

    /**
     * Sends the request and returns the response body as a raw, watchdog-tracked
     * {@link InputStream}.
     * <p>
     * Use this method to download binary content (files, images, etc.) from the server.
     * The caller is responsible for closing the stream. A stall past the effective
     * {@code idleTimeout} — whether before the first byte arrives or at any point
     * afterward — closes the stream automatically, unblocking a pending {@code read()}
     * with an {@link java.io.IOException}.
     *
     * @return The HTTP response whose body is a stream of the raw response bytes.
     */
    HttpResponse<InputStream> stream();

    /**
     * Sends the request asynchronously and returns the response body as a raw,
     * watchdog-tracked {@link InputStream}.
     * <p>
     * The caller is responsible for closing the stream when the future completes. See
     * {@link #stream()} for the same idle-timeout protection this method provides.
     *
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     */
    CompletableFuture<HttpResponse<InputStream>> streamAsync();
}
