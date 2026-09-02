package software.frisby.web.client.sse;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Values;
import software.frisby.web.serial.GenericType;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Package-private implementation of {@link SseBatchHandler}.
 * <p>
 * All validation lives here rather than in the {@link SseBatchHandler} static
 * factories, matching this codebase's convention of interfaces carrying no logic of
 * their own.
 */
final class DefaultSseBatchHandler implements SseBatchHandler {
    private static final String TYPE_ARGUMENT_NAME = "type";
    private static final String HANDLER_ARGUMENT_NAME = "handler";
    private static final String CAPACITY_ARGUMENT_NAME = "capacity";
    private static final String CONCURRENCY_ARGUMENT_NAME = "concurrency";
    private static final String BATCH_SIZE_ARGUMENT_NAME = "batchSize";
    private static final String BATCH_TIMEOUT_ARGUMENT_NAME = "batchTimeout";

    static final int DEFAULT_CAPACITY = 1024;
    static final int DEFAULT_CONCURRENCY = 1;
    static final int DEFAULT_BATCH_SIZE = 100;
    static final Duration DEFAULT_BATCH_TIMEOUT = Duration.ofMillis(250);

    private final Class<?> type;
    private final GenericType<?> genericType;
    private final Consumer<?> callback;
    private int capacity;
    private int concurrency;
    private int batchSize;
    private Duration batchTimeout;

    static DefaultSseBatchHandler ofType(Class<?> type, Consumer<?> callback) {
        Values.notNull(TYPE_ARGUMENT_NAME, type);
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseBatchHandler(type, null, callback);
    }

    static DefaultSseBatchHandler ofGenericType(GenericType<?> type, Consumer<?> callback) {
        Values.notNull(TYPE_ARGUMENT_NAME, type);
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseBatchHandler(null, type, callback);
    }

    static DefaultSseBatchHandler ofRaw(Consumer<?> callback) {
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseBatchHandler(null, null, callback);
    }

    private DefaultSseBatchHandler(Class<?> type, GenericType<?> genericType, Consumer<?> callback) {
        this.type = type;
        this.genericType = genericType;
        this.callback = callback;
        this.capacity = DEFAULT_CAPACITY;
        this.concurrency = DEFAULT_CONCURRENCY;
        this.batchSize = DEFAULT_BATCH_SIZE;
        this.batchTimeout = DEFAULT_BATCH_TIMEOUT;
    }

    @Override
    public SseBatchHandler capacity(int capacity) {
        this.capacity = Numbers.positive(CAPACITY_ARGUMENT_NAME, capacity);
        return this;
    }

    @Override
    public SseBatchHandler concurrency(int concurrency) {
        this.concurrency = Numbers.positive(CONCURRENCY_ARGUMENT_NAME, concurrency);
        return this;
    }

    @Override
    public SseBatchHandler batchSize(int batchSize) {
        this.batchSize = Numbers.positive(BATCH_SIZE_ARGUMENT_NAME, batchSize);
        return this;
    }

    @Override
    public SseBatchHandler batchTimeout(Duration batchTimeout) {
        this.batchTimeout = Durations.positive(BATCH_TIMEOUT_ARGUMENT_NAME, batchTimeout);
        return this;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int concurrency() {
        return concurrency;
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    @Override
    public Duration batchTimeout() {
        return batchTimeout;
    }

    @Override
    public Optional<Class<?>> type() {
        return Optional.ofNullable(type);
    }

    @Override
    public Optional<GenericType<?>> genericType() {
        return Optional.ofNullable(genericType);
    }

    @Override
    public Consumer<?> callback() {
        return callback;
    }
}

