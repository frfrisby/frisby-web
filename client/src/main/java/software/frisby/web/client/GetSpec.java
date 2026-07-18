package software.frisby.web.client;

import software.frisby.web.client.exception.UriSyntaxException;
import software.frisby.web.client.security.SecurityProvider;
import software.frisby.web.serial.GenericType;

import java.io.InputStream;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A fluent builder for configuring and executing an HTTP {@code GET} request.
 * <p>
 * Obtained from {@link Client#get()}.  Configure the request by chaining methods;
 * the request is not sent until a terminal method ({@link #send(Class)},
 * {@link #send(GenericType)}, or {@link #download()}) is called.
 *
 * <pre>{@code
 * // Strongly typed JSON response
 * User user = client.get()
 *         .path("/users/{id}", "id", userId)
 *         .parameter("include", "profile")
 *         .send(User.class)
 *         .body();
 *
 * // Generic collection
 * List<Order> orders = client.get()
 *         .path("/orders")
 *         .parameter("status", "open")
 *         .send(new GenericType<List<Order>>() {})
 *         .body();
 *
 * // File download
 * HttpResponse<InputStream> file = client.get()
 *         .path("/documents/{id}", "id", docId)
 *         .download();
 *
 * // Non-blocking — fire and move on
 * client.get()
 *         .path("/users/{id}", "id", userId)
 *         .sendAsync(User.class)
 *         .thenAccept(response -> process(response.body()));
 * }</pre>
 *
 * @see Client#get()
 */
public interface GetSpec {
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
    GetSpec path(String path);

    /**
     * Sets the URI path for this request, substituting a single named placeholder.
     * <p>
     * The placeholder must appear in the path surrounded by braces
     * (e.g. {@code {id}}).
     *
     * <pre>{@code
     * client.get().path("/users/{id}", "id", userId)
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
    GetSpec path(String path, String parameterId, String parameterValue);

    /**
     * Sets the URI path for this request, substituting one or more named placeholders.
     *
     * <pre>{@code
     * client.get().path(
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
    GetSpec path(String path, PathParameter... parameters);

    /**
     * Adds a query parameter to the request URI.
     *
     * @param name  The query parameter name.
     * @param value The query parameter value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     */
    GetSpec parameter(String name, String value);

    /**
     * Adds a multivalued query parameter to the request URI.  One {@code name=value}
     * pair is appended for each provided value.
     *
     * <pre>{@code
     * client.get().parameter("status", "open", "pending")
     * }</pre>
     *
     * @param name   The query parameter name.
     * @param values The query parameter values; one entry is added per value.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code name} is null or {@code values} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code values} is empty.
     * @throws software.frisby.core.validation.BlankValueException      if {@code name} is blank.
     */
    GetSpec parameter(String name, String... values);

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
    GetSpec header(String name, String value);

    /**
     * Adds a multivalued request header.  One header entry is added per value, which
     * is the correct representation for headers such as {@code Accept-Language} when
     * multiple values are needed.
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
    GetSpec header(String name, String... values);

    /**
     * Adds a cookie to the request.
     *
     * @param cookie The cookie to include.
     * @return This spec instance.
     * @throws software.frisby.core.validation.NullValueException if {@code cookie} is null.
     */
    GetSpec cookie(HttpCookie cookie);

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
    GetSpec security(SecurityProvider provider);

    /**
     * Sends the {@code GET} request and deserializes the JSON response body to the
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
     * Sends the {@code GET} request and deserializes the JSON response body to the
     * specified generic type.
     * <p>
     * Deserialization is only performed for {@code 2xx} responses.  See
     * {@link #send(Class)} for details on non-2xx behavior.
     *
     * <pre>{@code
     * List<Order> orders = client.get()
     *         .path("/orders")
     *         .send(new GenericType<List<Order>>() {})
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
     * Sends the {@code GET} request and returns the response body as a raw
     * {@link InputStream}.
     * <p>
     * Use this method to download binary content (files, images, etc.) from the server.
     * The caller is responsible for closing the stream.
     *
     * @return The HTTP response whose body is a stream of the raw response bytes.
     */
    HttpResponse<InputStream> download();

    /**
     * Sends the {@code GET} request asynchronously and deserializes the JSON response
     * body to the specified type.
     *
     * @param responseType The class of the expected response body type.
     * @param <T>          The response body type.
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     * @throws software.frisby.core.validation.NullValueException if {@code responseType} is null.
     */
    <T> CompletableFuture<HttpResponse<T>> sendAsync(Class<T> responseType);

    /**
     * Sends the {@code GET} request asynchronously and deserializes the JSON response
     * body to the specified generic type.
     *
     * <pre>{@code
     * client.get()
     *         .path("/orders")
     *         .sendAsync(new GenericType<List<Order>>() {})
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
     * Sends the {@code GET} request asynchronously and returns the response body as
     * a raw {@link InputStream}.
     * <p>
     * The caller is responsible for closing the stream when the future completes.
     *
     * @return A {@link CompletableFuture} that completes with the HTTP response.
     */
    CompletableFuture<HttpResponse<InputStream>> downloadAsync();
}
