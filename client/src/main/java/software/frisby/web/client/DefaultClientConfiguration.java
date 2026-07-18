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
    private static final String URI_FIELD = "uri";
    private static final String CONNECT_TIMEOUT = "connectTimeout";
    private static final String READ_TIMEOUT = "readTimeout";
    private static final String SERIALIZER = "serializer";
    private static final String REDIRECT_POLICY = "redirectPolicy";
    private static final String HTTP_VERSION = "httpVersion";
    private static final String LOGGING = "logging";
    private static final String DECOMPRESSORS = "decompressors";

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
        this.uri = Values.notNull(URI_FIELD, uri);
        this.connectTimeout = Values.notNull(CONNECT_TIMEOUT, connectTimeout);
        this.readTimeout = Values.notNull(READ_TIMEOUT, readTimeout);
        this.serializer = Values.notNull(SERIALIZER, serializer);
        this.sslContext = sslContext;
        this.redirectPolicy = Values.notNull(REDIRECT_POLICY, redirectPolicy);
        this.httpVersion = Values.notNull(HTTP_VERSION, httpVersion);
        this.decompressors = Sequences.notNull(DECOMPRESSORS, decompressors);

        if (!this.decompressors.isEmpty()) {
            Sequences.noDuplicates(DECOMPRESSORS, this.decompressors, ContentDecompressor::encoding);
        }

        this.executor = executor;
        this.logging = Values.notNull(LOGGING, logging);
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

