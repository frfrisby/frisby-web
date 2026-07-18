package software.frisby.web.server;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;
import software.frisby.web.serial.JsonSerializer;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

final class DefaultServerConfiguration implements ServerConfiguration {
    private final int port;
    private final String host;
    private final long maxRequestSize;
    private final boolean gzip;
    private final boolean http2;
    private final JsonSerializer serializer;
    private final SSLContext ssl;
    private final CorsConfiguration cors;
    private final ServerLoggingConfiguration logging;
    private final int maxConcurrentRequests;
    private final Executor executor;
    private final Duration stopTimeout;

    DefaultServerConfiguration(Integer port,
                               String host,
                               long maxRequestSize,
                               boolean gzip,
                               boolean http2,
                               JsonSerializer serializer,
                               SSLContext ssl,
                               CorsConfiguration cors,
                               ServerLoggingConfiguration logging,
                               int maxConcurrentRequests,
                               Executor executor,
                               Duration stopTimeout) {
        this.port = Numbers.range("port", port, 0, 65535);
        this.host = Strings.notBlank("host", host);
        this.maxRequestSize = Numbers.positive("maxRequestSize", maxRequestSize);
        this.gzip = gzip;
        this.http2 = http2;
        this.serializer = Values.notNull("serializer", serializer);
        this.ssl = ssl;
        this.cors = cors;
        this.logging = Values.notNull("logging", logging);
        this.maxConcurrentRequests = Numbers.positive("maxConcurrentRequests", maxConcurrentRequests);
        this.executor = executor;
        this.stopTimeout = Durations.optionalPositive("stopTimeout", stopTimeout);
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public long maxRequestSize() {
        return maxRequestSize;
    }

    @Override
    public boolean gzip() {
        return gzip;
    }

    @Override
    public boolean http2() {
        return http2;
    }

    @Override
    public JsonSerializer serializer() {
        return serializer;
    }

    @Override
    public Optional<SSLContext> ssl() {
        return Optional.ofNullable(ssl);
    }

    @Override
    public Optional<CorsConfiguration> cors() {
        return Optional.ofNullable(cors);
    }

    @Override
    public ServerLoggingConfiguration logging() {
        return logging;
    }

    @Override
    public int maxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.ofNullable(executor);
    }

    @Override
    public Optional<Duration> stopTimeout() {
        return Optional.ofNullable(stopTimeout);
    }
}
