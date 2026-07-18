package software.frisby.web.client;

import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * A builder for creating an instance of {@link ClientConfiguration}.
 * <p>
 * All options have documented defaults except {@link #uri(URI)},
 * {@link #connectTimeout(Duration)}, {@link #readTimeout(Duration)}, and
 * {@link #serializer(JsonSerializer)}, which are required.  {@link #build()} throws
 * an {@link IllegalStateException} if any required option is absent.
 *
 * <pre>{@code
 * ClientConfiguration config = ClientConfiguration.builder()
 *         .uri(URI.create("https://api.example.com"))
 *         .connectTimeout(Duration.ofSeconds(5))
 *         .readTimeout(Duration.ofSeconds(30))
 *         .serializer(new JacksonSerializer())
 *         .build();
 * }</pre>
 *
 * @see ClientConfiguration
 */
public interface ClientConfigurationBuilder {
    /**
     * Sets the base URI of the remote service.  Required.
     * <p>
     * All request paths are resolved against this URI.
     *
     * @param uri The base URI; must not be {@code null}.
     * @return This builder instance.
     */
    ClientConfigurationBuilder uri(URI uri);

    /**
     * Sets the maximum time to wait for a TCP connection to be established.  Required.
     *
     * @param timeout The connect timeout; must not be {@code null} or negative.
     * @return This builder instance.
     */
    ClientConfigurationBuilder connectTimeout(Duration timeout);

    /**
     * Sets the maximum time to wait for a response after a request has been sent.
     * Required.
     *
     * @param timeout The read timeout; must not be {@code null} or negative.
     * @return This builder instance.
     */
    ClientConfigurationBuilder readTimeout(Duration timeout);

    /**
     * Sets the JSON serializer used to serialize request bodies and deserialize
     * response bodies.  Required.
     * <p>
     * No default implementation is provided — {@link #build()} throws if this is absent.
     *
     * @param serializer The serializer to use; must not be {@code null}.
     * @return This builder instance.
     */
    ClientConfigurationBuilder serializer(JsonSerializer serializer);

    /**
     * Sets a custom {@link SSLContext} for non-standard TLS configurations (e.g.
     * a private CA trust store or mutual TLS client certificate).
     * <p>
     * Optional; defaults to the JDK default {@link SSLContext}.
     *
     * @param sslContext The SSL context to use; must not be {@code null}.
     * @return This builder instance.
     */
    ClientConfigurationBuilder sslContext(SSLContext sslContext);

    /**
     * Sets the redirect policy that controls whether the client automatically follows
     * HTTP redirects.
     * <p>
     * The default, {@link HttpClient.Redirect#NORMAL}, follows HTTP→HTTP and HTTPS→HTTPS
     * redirects transparently — callers receive the final destination response and do not
     * need to handle redirects themselves.
     * <p>
     * <strong>Using {@link HttpClient.Redirect#NEVER}:</strong>  When redirects are
     * disabled, a {@code 3xx} response is returned directly to the caller.  Response body
     * deserialization is only performed for {@code 2xx} responses, so
     * {@code response.body()} will be {@code null} for a {@code 3xx}.  The redirect
     * target is available via {@code response.headers().firstValue("Location")}.
     * Callers who set {@code NEVER} are responsible for inspecting the status code and
     * handling the redirect accordingly.
     * <p>
     * Optional; defaults to {@link HttpClient.Redirect#NORMAL}.
     *
     * @param policy The redirect policy; must not be {@code null}.
     * @return This builder instance.
     */
    ClientConfigurationBuilder redirectPolicy(HttpClient.Redirect policy);

    /**
     * Sets the HTTP protocol version preference.
     * <p>
     * Optional; defaults to {@link HttpClient.Version#HTTP_1_1}.
     *
     * @param version The HTTP version; must not be {@code null}.
     * @return This builder instance.
     */
    ClientConfigurationBuilder httpVersion(HttpClient.Version version);

    /**
     * Registers the built-in {@code gzip} response decompressor.
     * <p>
     * The client will advertise {@code Accept-Encoding: gzip} on every request and
     * decompress {@code Content-Encoding: gzip} responses automatically.
     * <p>
     * Calls to {@code decompress()} are additive — call it multiple times to support
     * multiple encodings.  The {@code Accept-Encoding} header value is derived from the
     * registration order.
     * <p>
     * Optional; no decompressors are registered by default.
     *
     * @return This builder instance.
     */
    ClientConfigurationBuilder decompress();

    /**
     * Registers a custom response decompressor for the encoding identified by
     * {@link ContentDecompressor#encoding()}.
     * <p>
     * The encoding token is advertised in the {@code Accept-Encoding} request header.
     * When the server responds with a matching {@code Content-Encoding} header, the
     * decompressor wraps the response stream before JSON deserialization.
     * <p>
     * Calls are additive — register multiple decompressors to support multiple encodings:
     *
     * <pre>{@code
     * ClientConfiguration.builder()
     *         .uri(serviceUri)
     *         .serializer(serializer)
     *         .decompress()                                                        // gzip
     *         .decompress(ContentDecompressor.of("br", BrotliInputStream::new))   // brotli
     *         .build();
     * }</pre>
     * <p>
     * Optional; no decompressors are registered by default.
     *
     * @param decompressor The decompressor to register; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code decompressor} is {@code null}.
     * @see #build()
     */
    ClientConfigurationBuilder decompress(ContentDecompressor decompressor);

    /**
     * Sets a custom {@link Executor} for the underlying {@link HttpClient}.
     * <p>
     * The shared default executor is sufficient for the vast majority of use cases —
     * it prevents thread exhaustion when multiple {@link Client}
     * instances are created.  Use this override only when bounded pools, virtual threads
     * (Java 21+), or custom thread monitoring are required.
     * <p>
     * Optional; defaults to the shared executor.
     *
     * @param executor The executor to use; must not be {@code null}.
     * @return This builder instance.
     */
    ClientConfigurationBuilder executor(Executor executor);

    /**
     * Sets the logging configuration that controls header masking, body field
     * redaction, and body size limits for log entries.
     * <p>
     * The built-in defaults — {@code Authorization}, {@code Cookie}, and
     * {@code Set-Cookie} — are always masked regardless of this setting.  Use
     * {@link ClientLoggingConfigurationBuilder#redactHeaders(String...)} to add
     * service-specific headers (e.g. {@code X-Api-Key}).  Use
     * {@link ClientLoggingConfigurationBuilder#redactFields(String...)} to suppress
     * sensitive JSON field values from both request and response bodies in log entries.
     * <p>
     * Optional; defaults to 8 KB body size limit, no custom redacted fields, and
     * only the three built-in headers masked.
     *
     * @param logging The logging configuration; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code logging} is {@code null}.
     */
    ClientConfigurationBuilder logging(ClientLoggingConfiguration logging);

    /**
     * Returns a new {@link ClientConfiguration} instance based on the provided options.
     *
     * @return A new {@link ClientConfiguration} instance.
     * @throws IllegalStateException                                      if any required option ({@code uri},
     *                                                                    {@code connectTimeout}, {@code readTimeout},
     *                                                                    or {@code serializer}) is absent.
     * @throws software.frisby.core.validation.DuplicateElementsException if two or more registered
     *                                                                    decompressors share the same {@link ContentDecompressor#encoding()} value.
     */
    ClientConfiguration build();
}
