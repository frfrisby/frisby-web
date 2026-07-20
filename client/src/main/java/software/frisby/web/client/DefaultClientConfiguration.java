package software.frisby.web.client;

import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.Values;
import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.Executor;

final class DefaultClientConfiguration implements ClientConfiguration {
    private static final String URI_ARGUMENT_NAME = "uri";
    private static final String CONNECT_TIMEOUT_ARGUMENT_NAME = "connectTimeout";
    private static final String READ_TIMEOUT_ARGUMENT_NAME = "readTimeout";
    private static final String SERIALIZER_ARGUMENT_NAME = "serializer";
    private static final String REDIRECT_POLICY_ARGUMENT_NAME = "redirectPolicy";
    private static final String HTTP_VERSION_ARGUMENT_NAME = "httpVersion";
    private static final String LOGGING_ARGUMENT_NAME = "logging";
    private static final String DECOMPRESSORS_ARGUMENT_NAME = "decompressors";

    private final URI uri;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final JsonSerializer serializer;
    private final SSLContext sslContext;
    private final HttpClient.Redirect redirectPolicy;
    private final HttpClient.Version httpVersion;
    private final List<ContentDecompressor> decompressors;
    private final Executor executor;
    private final ClientLoggingConfiguration logging;

    @SuppressWarnings("java:S107") // all parameters are required — this class is the product of DefaultConfigurationBuilder
    DefaultClientConfiguration(URI uri,
                               Duration connectTimeout,
                               Duration readTimeout,
                               JsonSerializer serializer,
                               SSLContext sslContext,
                               HttpClient.Redirect redirectPolicy,
                               HttpClient.Version httpVersion,
                               List<ContentDecompressor> decompressors,
                               Executor executor,
                               ClientLoggingConfiguration logging) {
        this.uri = Values.notNull(URI_ARGUMENT_NAME, uri);
        this.connectTimeout = Values.notNull(CONNECT_TIMEOUT_ARGUMENT_NAME, connectTimeout);
        this.readTimeout = Values.notNull(READ_TIMEOUT_ARGUMENT_NAME, readTimeout);
        this.serializer = Values.notNull(SERIALIZER_ARGUMENT_NAME, serializer);
        this.sslContext = sslContext;
        this.redirectPolicy = Values.notNull(REDIRECT_POLICY_ARGUMENT_NAME, redirectPolicy);
        this.httpVersion = Values.notNull(HTTP_VERSION_ARGUMENT_NAME, httpVersion);
        this.decompressors = Sequences.notNull(DECOMPRESSORS_ARGUMENT_NAME, decompressors);

        if (!this.decompressors.isEmpty()) {
            Sequences.noDuplicates(DECOMPRESSORS_ARGUMENT_NAME, this.decompressors, ContentDecompressor::encoding);
        }

        this.executor = executor;
        this.logging = Values.notNull(LOGGING_ARGUMENT_NAME, logging);
    }

    /**
     * Returns the derived {@code Accept-Encoding} header value from the provided
     * decompressor list, or {@code null} when the list is empty.
     * <p>
     * Example: if {@code gzip} and {@code br} decompressors are registered (in that
     * order), returns {@code "gzip, br"}.
     */
    static String acceptEncoding(List<ContentDecompressor> decompressors) {
        if (decompressors.isEmpty()) {
            return null;
        }

        StringJoiner joiner = new StringJoiner(", ");

        for (ContentDecompressor decompressor : decompressors) {
            joiner.add(decompressor.encoding());
        }

        return joiner.toString();
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public JsonSerializer serializer() {
        return serializer;
    }

    @Override
    public Optional<SSLContext> sslContext() {
        return Optional.ofNullable(sslContext);
    }

    @Override
    public HttpClient.Redirect redirectPolicy() {
        return redirectPolicy;
    }

    @Override
    public HttpClient.Version httpVersion() {
        return httpVersion;
    }

    @Override
    public List<ContentDecompressor> decompressors() {
        return decompressors;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.ofNullable(executor);
    }

    @Override
    public ClientLoggingConfiguration logging() {
        return logging;
    }
}

