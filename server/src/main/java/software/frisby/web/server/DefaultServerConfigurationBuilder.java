package software.frisby.web.server;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;
import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.concurrent.Executor;

final class DefaultServerConfigurationBuilder implements ServerConfigurationBuilder {
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final long DEFAULT_MAX_REQUEST_SIZE = 4L * 1024L * 1024L;

    private static final String PORT_ARGUMENT_NAME = "port";
    private static final String HOST_ARGUMENT_NAME = "host";
    private static final String MAX_REQUEST_SIZE_ARGUMENT_NAME = "maxRequestSize";
    private static final String SERIALIZER_ARGUMENT_NAME = "serializer";
    private static final String SSL_CONTEXT_ARGUMENT_NAME = "sslContext";
    private static final String CORS_ARGUMENT_NAME = "cors";
    private static final String LOGGING_ARGUMENT_NAME = "logging";
    private static final String MAX_CONCURRENT_REQUESTS_ARGUMENT_NAME = "maxConcurrentRequests";
    private static final String EXECUTOR_ARGUMENT_NAME = "executor";
    private static final String STOP_TIMEOUT_ARGUMENT_NAME = "timeout";


    private Integer port;
    private String host;
    private long maxRequestSize;
    private boolean gzip;
    private boolean http2;
    private JsonSerializer serializer;
    private SSLContext sslContext;
    private CorsConfiguration cors;
    private ServerLoggingConfiguration logging;
    private Integer maxConcurrentRequests;
    private Executor executor;
    private Duration stopTimeout;

    DefaultServerConfigurationBuilder() {
        this.port = null;
        this.host = DEFAULT_HOST;
        this.maxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
        this.gzip = false;
        this.http2 = false;
        this.serializer = null;
        this.sslContext = null;
        this.cors = null;
        this.logging = ServerLoggingConfiguration.builder().build();
        this.maxConcurrentRequests = null;
        this.executor = null;
        this.stopTimeout = null;
    }

    @Override
    public ServerConfigurationBuilder port(int port) {
        this.port = Numbers.range(PORT_ARGUMENT_NAME, port, 0, 65535);
        return this;
    }

    @Override
    public ServerConfigurationBuilder host(String host) {
        this.host = Strings.notBlank(HOST_ARGUMENT_NAME, host);
        return this;
    }

    @Override
    public ServerConfigurationBuilder maxRequestSize(long maxRequestSize) {
        this.maxRequestSize = Numbers.positive(MAX_REQUEST_SIZE_ARGUMENT_NAME, maxRequestSize);
        return this;
    }

    @Override
    public ServerConfigurationBuilder serializer(JsonSerializer serializer) {
        this.serializer = Values.notNull(SERIALIZER_ARGUMENT_NAME, serializer);
        return this;
    }

    @Override
    public ServerConfigurationBuilder ssl(SSLContext sslContext) {
        this.sslContext = Values.notNull(SSL_CONTEXT_ARGUMENT_NAME, sslContext);
        return this;
    }

    @Override
    public ServerConfigurationBuilder cors(CorsConfiguration cors) {
        this.cors = Values.notNull(CORS_ARGUMENT_NAME, cors);
        return this;
    }

    @Override
    public ServerConfigurationBuilder gzip() {
        this.gzip = true;
        return this;
    }

    @Override
    public ServerConfigurationBuilder http2() {
        this.http2 = true;
        return this;
    }

    @Override
    public ServerConfigurationBuilder logging(ServerLoggingConfiguration logging) {
        this.logging = Values.notNull(LOGGING_ARGUMENT_NAME, logging);
        return this;
    }

    @Override
    public ServerConfigurationBuilder maxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = Numbers.positive(MAX_CONCURRENT_REQUESTS_ARGUMENT_NAME, maxConcurrentRequests);
        return this;
    }

    @Override
    public ServerConfigurationBuilder executor(Executor executor) {
        this.executor = Values.notNull(EXECUTOR_ARGUMENT_NAME, executor);
        return this;
    }

    @Override
    public ServerConfigurationBuilder stopTimeout(Duration timeout) {
        Durations.positive(STOP_TIMEOUT_ARGUMENT_NAME, timeout);
        this.stopTimeout = timeout;
        return this;
    }

    @Override
    public ServerConfiguration build() {

        int effectiveMaxConcurrent = null != maxConcurrentRequests
                ? maxConcurrentRequests
                : Runtime.getRuntime().availableProcessors() * 20;

        return new DefaultServerConfiguration(
                port,
                host,
                maxRequestSize,
                gzip,
                http2,
                serializer,
                sslContext,
                cors,
                logging,
                effectiveMaxConcurrent,
                executor,
                stopTimeout
        );
    }
}
