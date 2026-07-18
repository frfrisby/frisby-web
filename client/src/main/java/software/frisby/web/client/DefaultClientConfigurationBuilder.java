package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;

final class DefaultClientConfigurationBuilder implements ClientConfigurationBuilder {
    private static final String URI_FIELD = "uri";
    private static final String CONNECT_TIMEOUT = "connectTimeout";
    private static final String READ_TIMEOUT = "readTimeout";
    private static final String SERIALIZER = "serializer";
    private static final String SSL_CONTEXT = "sslContext";
    private static final String REDIRECT_POLICY = "redirectPolicy";
    private static final String HTTP_VERSION = "httpVersion";
    private static final String EXECUTOR = "executor";
    private static final String LOGGING = "logging";
    private static final String DECOMPRESSOR = "decompressor";

    private static final ContentDecompressor GZIP_DECOMPRESSOR =
            ContentDecompressor.of("gzip", GZIPInputStream::new);
    private final List<ContentDecompressor> decompressors;
    private URI uri;
    private Duration connectTimeout;
    private Duration readTimeout;
    private JsonSerializer serializer;
    private SSLContext sslContext;
    private HttpClient.Redirect redirectPolicy;
    private HttpClient.Version httpVersion;
    private Executor executor;
    private ClientLoggingConfiguration logging;

    DefaultClientConfigurationBuilder() {
        this.uri = null;
        this.connectTimeout = null;
        this.readTimeout = null;
        this.serializer = null;
        this.sslContext = null;
        this.redirectPolicy = HttpClient.Redirect.NORMAL;
        this.httpVersion = HttpClient.Version.HTTP_1_1;
        this.decompressors = new ArrayList<>();
        this.executor = null;
        this.logging = ClientLoggingConfiguration.builder().build();
    }

    @Override
    public ClientConfigurationBuilder uri(URI uri) {
        this.uri = Values.notNull(URI_FIELD, uri);
        return this;
    }

    @Override
    public ClientConfigurationBuilder connectTimeout(Duration timeout) {
        this.connectTimeout = Values.notNull(CONNECT_TIMEOUT, timeout);
        return this;
    }

    @Override
    public ClientConfigurationBuilder readTimeout(Duration timeout) {
        this.readTimeout = Values.notNull(READ_TIMEOUT, timeout);
        return this;
    }

    @Override
    public ClientConfigurationBuilder serializer(JsonSerializer serializer) {
        this.serializer = Values.notNull(SERIALIZER, serializer);
        return this;
    }

    @Override
    public ClientConfigurationBuilder sslContext(SSLContext sslContext) {
        this.sslContext = Values.notNull(SSL_CONTEXT, sslContext);
        return this;
    }

    @Override
    public ClientConfigurationBuilder redirectPolicy(HttpClient.Redirect policy) {
        this.redirectPolicy = Values.notNull(REDIRECT_POLICY, policy);
        return this;
    }

    @Override
    public ClientConfigurationBuilder httpVersion(HttpClient.Version version) {
        this.httpVersion = Values.notNull(HTTP_VERSION, version);
        return this;
    }

    @Override
    public ClientConfigurationBuilder decompress() {
        return decompress(GZIP_DECOMPRESSOR);
    }

    @Override
    public ClientConfigurationBuilder decompress(ContentDecompressor decompressor) {
        decompressors.add(Values.notNull(DECOMPRESSOR, decompressor));
        return this;
    }

    @Override
    public ClientConfigurationBuilder executor(Executor executor) {
        this.executor = Values.notNull(EXECUTOR, executor);
        return this;
    }

    @Override
    public ClientConfigurationBuilder logging(ClientLoggingConfiguration logging) {
        this.logging = Values.notNull(LOGGING, logging);
        return this;
    }

    @Override
    public ClientConfiguration build() {
        return new DefaultClientConfiguration(
                uri,
                connectTimeout,
                readTimeout,
                serializer,
                sslContext,
                redirectPolicy,
                httpVersion,
                List.copyOf(decompressors),
                executor,
                logging
        );
    }
}

