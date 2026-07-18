package software.frisby.web.client;

import software.frisby.web.client.exception.UriSyntaxException;
import software.frisby.web.client.security.SecurityProvider;

import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A fluent builder for configuring and executing an HTTP {@code HEAD} request.
 * <p>
 * Obtained from {@link Client#head()}.  Configure the request by chaining methods;
 * the request is not sent until {@link #send()} or {@link #sendAsync()} is called.
 * <p>
 * {@code HEAD} is identical to {@code GET} but the server returns only the response
 * headers — no body is transmitted.  Common use cases include:
 * <ul>
 *   <li>Checking whether a resource exists ({@code 200} vs {@code 404}) without
 *       incurring the cost of downloading its body</li>
 *   <li>Reading {@code Content-Length} to decide whether a download is worth initiating</li>
 *   <li>Cache validation via {@code ETag} or {@code Last-Modified} headers</li>
 *   <li>Lightweight health or liveness probes</li>
 * </ul>
 *
 * <pre>{@code
 * // Check if a resource exists
 * int status = client.head()
 *         .path("/documents/{id}", "id", docId)
 *         .send()
 *         .statusCode();
 *
 * // Read Content-Length before downloading
 * HttpResponse<Void> meta = client.head()
 *         .path("/files/{id}", "id", fileId)
 *         .send();
 * long size = meta.headers()
 *         .firstValueAsLong("Content-Length")
 *         .orElse(-1L);
 *
 * // Non-blocking existence check
 * client.head()
 *         .path("/documents/{id}", "id", docId)
 *         .sendAsync()
 *         .thenAccept(response -> handleStatus(response.statusCode()));
 * }</pre>
 *
 * @see Client#head()
 */
public interface HeadSpec {
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
    HeadSpec path(String path);

    /**
     * Sets the URI path for this request, substituting a single named placeholder.
     * <p>
     * The placeholder must appear in the path surrounded by braces
     * (e.g. {@code {id}}).
     *
     * <pre>{@code
     * client.head().path("/documents/{id}", "id", docId)
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
    HeadSpec path(String path, String parameterId, String parameterValue);

    /**
     * Sets the URI path for this request, substituting one or more named placeholders.
     *
     * <pre>{@code
     * client.head().path(
     *         "/teams/{teamId}/members/{memberId}",
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
    HeadSpec path(String path, PathParameter... parameters);

    /**
     * Adds a query parameter to the request URI.
     *
     * @param name  The query parameter name.
     * @param value The query parameter value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     */
    HeadSpec parameter(String name, String value);

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
    HeadSpec parameter(String name, String... values);

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
    HeadSpec header(String name, String value);

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
    HeadSpec header(String name, String... values);

    /**
     * Adds a cookie to the request.
     *
     * @param cookie The cookie to include.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code cookie} is null.
     */
    HeadSpec cookie(HttpCookie cookie);

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
    HeadSpec security(SecurityProvider provider);

    /**
     * Sends the {@code HEAD} request.
     * <p>
     * The response body is always empty; inspect the response headers for metadata.
     *
     * @return The HTTP response.
     */
    HttpResponse<Void> send();

    /**
     * Sends the {@code HEAD} request asynchronously.
     * <p>
     * The response body is always empty; inspect the response headers for metadata.
     *
     * <pre>{@code
     * client.head()
     *         .path("/documents/{id}", "id", docId)
     *         .sendAsync()
     *         .thenAccept(response -> handleStatus(response.statusCode()));
     * }</pre>
     *
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     */
    CompletableFuture<HttpResponse<Void>> sendAsync();
}
