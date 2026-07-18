package software.frisby.web.client;

import software.frisby.web.client.exception.UriSyntaxException;
import software.frisby.web.client.security.SecurityProvider;
import software.frisby.web.serial.GenericType;

import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A fluent builder for configuring and executing an HTTP {@code PATCH} request.
 * <p>
 * Obtained from {@link Client#patch()}.  Configure the request by chaining methods
 * in any order, call one of the {@code body()} methods to provide the request body,
 * then call one of the {@code send()} methods to execute the request.
 * <p>
 * {@code PATCH} is designed to carry a <em>description of changes</em> to apply to an
 * existing resource, as opposed to {@code PUT} which replaces the resource entirely.
 * Common body formats include:
 * <ul>
 *   <li>{@code application/json} — informal partial update; set the body via
 *       {@link #body(Object)} and send with the default {@code Content-Type}</li>
 *   <li>{@code application/merge-patch+json} (RFC 7396) — formal merge-patch; use
 *       {@link #body(Object)} and set the {@code Content-Type} header explicitly via
 *       {@link #header(String, String)} using {@link Headers#CONTENT_TYPE}</li>
 *   <li>{@code application/json-patch+json} (RFC 6902) — structured operation list;
 *       same approach as merge-patch</li>
 * </ul>
 * <p>
 * <strong>Note on {@code multipart/form-data}:</strong> {@code PATCH} intentionally does
 * not support {@link FormData} multipart bodies.  {@code PATCH} semantics describe a
 * <em>change set</em> to an existing resource; multipart file upload — the primary use
 * case for {@link FormData} — belongs on {@link PostSpec} or {@link PutSpec}.  If a
 * future API genuinely requires multipart {@code PATCH}, support can be added as a
 * non-breaking addition to this interface.
 *
 * <pre>{@code
 * // Informal JSON merge patch, no response body
 * client.patch()
 *         .path("/users/{id}", "id", userId)
 *         .body(Map.of("email", "new@example.com"))
 *         .send();
 *
 * // Formal merge-patch (RFC 7396), typed response
 * User updated = client.patch()
 *         .path("/users/{id}", "id", userId)
 *         .header(Headers.CONTENT_TYPE, "application/merge-patch+json")
 *         .body(partialUser)
 *         .send(User.class)
 *         .body();
 *
 * // PATCH with gzip-compressed body (large merge payloads)
 * client.patch()
 *         .path("/data/bulk/{id}", "id", batchId)
 *         .compress()
 *         .body(largePatch)
 *         .send();
 * }</pre>
 *
 * @see Client#patch()
 */
public interface PatchSpec {
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
    PatchSpec path(String path);

    /**
     * Sets the URI path for this request, substituting a single named placeholder.
     * <p>
     * The placeholder must appear in the path surrounded by braces
     * (e.g. {@code {id}}).
     *
     * <pre>{@code
     * client.patch().path("/users/{id}", "id", userId)
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
    PatchSpec path(String path, String parameterId, String parameterValue);

    /**
     * Sets the URI path for this request, substituting one or more named placeholders.
     *
     * <pre>{@code
     * client.patch().path(
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
    PatchSpec path(String path, PathParameter... parameters);

    /**
     * Adds a query parameter to the request URI.
     *
     * @param name  The query parameter name.
     * @param value The query parameter value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     */
    PatchSpec parameter(String name, String value);

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
    PatchSpec parameter(String name, String... values);

    /**
     * Adds a request header.
     * <p>
     * The following headers are managed by the client and must not be set here:
     * {@link Headers#ACCEPT}, {@link Headers#ACCEPT_ENCODING},
     * {@link Headers#CONTENT_TYPE}, {@link Headers#CONTENT_LENGTH},
     * {@link Headers#CONTENT_ENCODING}, and {@link Headers#TRANSFER_ENCODING}.
     * Attempting to set any of these will throw an {@link IllegalArgumentException}.
     * <p>
     * For {@code PATCH}, you may also need to set {@code Content-Type} explicitly — see
     * the class-level Javadoc for details.
     *
     * @param name  The header name.  See {@link Headers} for well-known header name constants.
     * @param value The header value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     * @throws IllegalArgumentException                            if {@code name} is a client-managed header.
     */
    PatchSpec header(String name, String value);

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
    PatchSpec header(String name, String... values);

    /**
     * Adds a cookie to the request.
     *
     * @param cookie The cookie to include.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code cookie} is null.
     */
    PatchSpec cookie(HttpCookie cookie);

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
    PatchSpec security(SecurityProvider provider);

    /**
     * Enables gzip compression of the JSON request body.
     * <p>
     * Sets {@code Content-Encoding: gzip} on the request automatically.  Only use
     * this when the target server is known to support receiving compressed request bodies.
     * <p>
     * Compression applies only when the request body is a JSON entity (provided via
     * {@link #body(Object)}).  Calling this method when the request body is a
     * {@link FormUrlEncoded} payload will throw an {@link IllegalStateException} at
     * send time.
     *
     * @return This spec instance.
     */
    PatchSpec compress();

    /**
     * Enables compression of the JSON request body using a custom algorithm.
     * <p>
     * The {@link ContentCompressor#encoding()} value is set as the
     * {@code Content-Encoding} header automatically.  Use the no-arg
     * {@link #compress()} for the built-in {@code gzip} algorithm.
     * <p>
     * Compression applies only when the request body is a JSON entity (provided via
     * {@link #body(Object)}).  Calling this method when the request body is a
     * {@link FormUrlEncoded} payload will throw an {@link IllegalStateException} at
     * send time.
     *
     * @param compressor The compression algorithm and implementation; must not be {@code null}.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code compressor} is null.
     */
    PatchSpec compress(ContentCompressor compressor);

    /**
     * Provides the JSON request body.
     * <p>
     * The body will be serialized to JSON using the
     * {@link software.frisby.web.serial.JsonSerializer} configured on the client.
     * If the body is already a {@link String}, it is sent as-is without additional
     * serialization.
     * <p>
     * If {@link #compress()} or {@link #compress(ContentCompressor)} was called, the serialized body will be
     * compressed before being sent.
     * <p>
     * To use a formal PATCH format such as {@code application/merge-patch+json} (RFC 7396)
     * or {@code application/json-patch+json} (RFC 6902), set the {@link Headers#CONTENT_TYPE}
     * header explicitly before calling {@code body()}.
     *
     * @param body The object to serialize as the JSON request body.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code body} is null.
     */
    PatchSpec body(Object body);

    /**
     * Provides an {@code application/x-www-form-urlencoded} request body.
     * <p>
     * If {@link #compress()} or {@link #compress(ContentCompressor)} was called, an {@link IllegalStateException}
     * will be thrown at send time — compression is not supported for form-urlencoded requests.
     *
     * @param formUrlEncoded The URL-encoded form fields.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code formUrlEncoded} is null.
     */
    PatchSpec body(FormUrlEncoded formUrlEncoded);

    /**
     * Sends the {@code PATCH} request and deserializes the JSON response body to the
     * specified type.
     * <p>
     * Deserialization is only performed for {@code 2xx} responses.  If the server
     * returns a {@code 3xx} and the redirect policy is set to
     * {@link java.net.http.HttpClient.Redirect#NEVER}, {@code response.body()} will be
     * {@code null} — always check {@code response.statusCode()} before using the body.
     *
     * @param responseType The class of the expected response body type.
     * @param <T>          The response body type.
     * @return The HTTP response containing the deserialized body, or {@code null} body
     * for non-2xx responses that do not throw.
     * @throws software.frisby.core.validation.NullValueException if {@code responseType} is null.
     */
    <T> HttpResponse<T> send(Class<T> responseType);

    /**
     * Sends the {@code PATCH} request and deserializes the JSON response body to the
     * specified generic type.
     * <p>
     * Deserialization is only performed for {@code 2xx} responses.  See
     * {@link #send(Class)} for details on non-2xx behavior.
     *
     * <pre>{@code
     * List<Item> updated = client.patch()
     *         .path("/collections/{id}", "id", collectionId)
     *         .body(patchOps)
     *         .send(new GenericType<List<Item>>() {})
     *         .body();
     * }</pre>
     *
     * @param responseType The generic type token of the expected response body.
     * @param <T>          The response body type.
     * @return The HTTP response containing the deserialized body, or {@code null} body
     * for non-2xx responses that do not throw.
     * @throws software.frisby.core.validation.NullValueException if {@code responseType} is null.
     */
    <T> HttpResponse<T> send(GenericType<T> responseType);

    /**
     * Sends the {@code PATCH} request without expecting a response body.
     *
     * @return The HTTP response.  The response body is empty.
     */
    HttpResponse<Void> send();

    /**
     * Sends the {@code PATCH} request asynchronously and deserializes the JSON response
     * body to the specified type.
     *
     * @param responseType The class of the expected response body type.
     * @param <T>          The response body type.
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     * @throws software.frisby.core.validation.NullValueException if {@code responseType} is null.
     */
    <T> CompletableFuture<HttpResponse<T>> sendAsync(Class<T> responseType);

    /**
     * Sends the {@code PATCH} request asynchronously and deserializes the JSON response
     * body to the specified generic type.
     *
     * <pre>{@code
     * client.patch()
     *         .path("/users/{id}", "id", userId)
     *         .body(partialUser)
     *         .sendAsync(new GenericType<List<String>>() {})
     *         .thenAccept(response -> process(response.body()));
     * }</pre>
     *
     * @param responseType The generic type token of the expected response body.
     * @param <T>          The response body type.
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     * @throws software.frisby.core.validation.NullValueException if {@code responseType} is null.
     */
    <T> CompletableFuture<HttpResponse<T>> sendAsync(GenericType<T> responseType);

    /**
     * Sends the {@code PATCH} request asynchronously without expecting a response body.
     *
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     */
    CompletableFuture<HttpResponse<Void>> sendAsync();
}
