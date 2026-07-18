package software.frisby.web.client;

/**
 * A synchronous HTTP client for invoking remote HTTP services.
 * <p>
 * Create an instance via {@link ClientBuilder} and reuse it for the lifetime of the
 * application.  Each instance is configured with a single base URI; one client
 * per target service is the recommended pattern.
 *
 * <pre>{@code
 * Client client = Client.builder()
 *         .configuration(
 *                 ClientConfiguration.builder()
 *                         .uri(URI.create("https://api.example.com"))
 *                         .connectTimeout(Duration.ofSeconds(5))
 *                         .readTimeout(Duration.ofSeconds(30))
 *                         .serializer(myJsonSerializer)
 *                         .build()
 *         )
 *         .build();
 *
 * User user = client.get()
 *         .path("/users/{id}", "id", userId)
 *         .send(User.class)
 *         .body();
 * }</pre>
 *
 * @see GetSpec
 * @see PostSpec
 * @see PutSpec
 * @see PatchSpec
 * @see DeleteSpec
 * @see HeadSpec
 */
public interface Client {
    /**
     * Returns a new {@link ClientBuilder} instance.
     *
     * @return A new builder.
     */
    static ClientBuilder builder() {
        return new DefaultClientBuilder();
    }

    /**
     * Begins a fluent {@code GET} request.
     *
     * @return A {@link GetSpec} to configure and execute the request.
     */
    GetSpec get();

    /**
     * Begins a fluent {@code POST} request.
     *
     * @return A {@link PostSpec} to configure and execute the request.
     */
    PostSpec post();

    /**
     * Begins a fluent {@code PUT} request.
     *
     * @return A {@link PutSpec} to configure and execute the request.
     */
    PutSpec put();

    /**
     * Begins a fluent {@code PATCH} request.
     *
     * @return A {@link PatchSpec} to configure and execute the request.
     */
    PatchSpec patch();

    /**
     * Begins a fluent {@code DELETE} request.
     *
     * @return A {@link DeleteSpec} to configure and execute the request.
     */
    DeleteSpec delete();

    /**
     * Begins a fluent {@code HEAD} request.
     * <p>
     * Use {@code HEAD} to retrieve response headers for a resource without downloading
     * its body — useful for existence checks, cache validation, and pre-flight size
     * inspection.
     *
     * @return A {@link HeadSpec} to configure and execute the request.
     */
    HeadSpec head();

    /**
     * Returns the {@link ClientConfiguration} used to create this client instance.
     *
     * @return The client configuration.
     */
    ClientConfiguration configuration();
}

