package software.frisby.web.client;

import software.frisby.web.client.exception.UnsupportedContentEncodingException;
import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Defines the configuration options used by a {@link Client} to connect to a remote
 * HTTP service.
 * <p>
 * Create an instance via {@link #builder()}.
 *
 * @see ClientConfigurationBuilder
 */
public interface ClientConfiguration {
    /**
     * Returns a new builder for constructing a {@link ClientConfiguration} instance.
     *
     * @return A new {@link ClientConfigurationBuilder}.
     */
    static ClientConfigurationBuilder builder() {
        return new DefaultClientConfigurationBuilder();
    }

    /**
     * Returns the base URI of the remote service.
     * <p>
     * All request paths are resolved against this URI.
     *
     * @return The base URI.
     */
    URI uri();

    /**
     * Returns the maximum time to wait for a TCP connection to be established.
     *
     * @return The connect timeout.
     */
    Duration connectTimeout();

    /**
     * Returns the maximum time to wait for a response after a request has been sent.
     *
     * @return The read timeout.
     */
    Duration readTimeout();

    /**
     * Returns the JSON serializer used to serialize request bodies and deserialize
     * response bodies.
     *
     * @return The {@link JsonSerializer} instance.
     */
    JsonSerializer serializer();

    /**
     * Returns the optional {@link SSLContext} used to customize TLS settings such as
     * a non-standard trust store or client certificate.
     * <p>
     * When empty, the JDK default {@link SSLContext} is used.
     *
     * @return The {@link SSLContext} when configured; otherwise empty.
     */
    Optional<SSLContext> sslContext();

    /**
     * Returns the redirect policy that controls whether the client automatically
     * follows HTTP redirects.
     * <p>
     * Defaults to {@link HttpClient.Redirect#NORMAL}.
     *
     * @return The redirect policy.
     */
    HttpClient.Redirect redirectPolicy();

    /**
     * Returns the HTTP protocol version preference.
     * <p>
     * Defaults to {@link HttpClient.Version#HTTP_1_1}.
     *
     * @return The HTTP version.
     */
    HttpClient.Version httpVersion();

    /**
     * Returns the ordered list of registered response decompressors.
     * <p>
     * Each decompressor handles one {@code Content-Encoding} token.  The order of the
     * list determines the value of the {@code Accept-Encoding} request header — the
     * client advertises all registered encodings in registration order.
     * <p>
     * When empty, no {@code Accept-Encoding} header is sent and responses with a
     * {@code Content-Encoding} header will throw
     * {@link UnsupportedContentEncodingException}.
     *
     * @return An unmodifiable list of decompressors; never {@code null}.
     */
    List<ContentDecompressor> decompressors();

    /**
     * Returns the optional custom {@link Executor} used by the underlying
     * {@link HttpClient} for async operations.
     * <p>
     * When empty, the shared default executor is used — the recommended choice for
     * most applications.
     *
     * @return The {@link Executor} when configured; otherwise empty.
     */
    Optional<Executor> executor();

    /**
     * Returns the logging configuration that controls header masking, body field
     * redaction, and body size limits for log entries.
     * <p>
     * When {@link ClientConfigurationBuilder#logging(ClientLoggingConfiguration)} is not called,
     * a default configuration is used: 8 KB body size limit, no custom redacted fields, and
     * only the three built-in headers masked ({@code Authorization}, {@code Cookie},
     * {@code Set-Cookie}).
     *
     * @return The logging configuration; never {@code null}.
     */
    ClientLoggingConfiguration logging();
}
