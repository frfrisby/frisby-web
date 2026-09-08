package software.frisby.web.client.sse;

import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Values;
import software.frisby.web.serial.GenericType;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Package-private implementation of {@link SseHandler}.
 * <p>
 * All validation lives here rather than in the {@link SseHandler} static factories,
 * matching this codebase's convention of interfaces carrying no logic of their own.
 */
final class DefaultSseHandler<T> implements SseHandler<T> {
    private static final String TYPE_ARGUMENT_NAME = "type";
    private static final String HANDLER_ARGUMENT_NAME = "handler";
    private static final String CAPACITY_ARGUMENT_NAME = "capacity";
    private static final String CONCURRENCY_ARGUMENT_NAME = "concurrency";

    static final int DEFAULT_CAPACITY = 1024;
    static final int DEFAULT_CONCURRENCY = 1;

    private final Class<T> type;
    private final GenericType<T> genericType;
    private final Consumer<SseMessage<T>> callback;
    private int capacity;
    private int concurrency;

    private DefaultSseHandler(Class<T> type, GenericType<T> genericType, Consumer<SseMessage<T>> callback) {
        this.type = type;
        this.genericType = genericType;
        this.callback = callback;
        this.capacity = DEFAULT_CAPACITY;
        this.concurrency = DEFAULT_CONCURRENCY;
    }

    static <T> DefaultSseHandler<T> ofType(Class<T> type, Consumer<SseMessage<T>> callback) {
        Values.notNull(TYPE_ARGUMENT_NAME, type);
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseHandler<>(type, null, callback);
    }

    static <T> DefaultSseHandler<T> ofGenericType(GenericType<T> type, Consumer<SseMessage<T>> callback) {
        Values.notNull(TYPE_ARGUMENT_NAME, type);
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseHandler<>(null, type, callback);
    }

    static <T> DefaultSseHandler<T> ofRaw(Consumer<SseMessage<T>> callback) {
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseHandler<>(null, null, callback);
    }


    @Override
    public SseHandler<T> capacity(int capacity) {
        this.capacity = Numbers.positive(CAPACITY_ARGUMENT_NAME, capacity);
        return this;
    }

    @Override
    public SseHandler<T> concurrency(int concurrency) {
        this.concurrency = Numbers.positive(CONCURRENCY_ARGUMENT_NAME, concurrency);
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
    public Optional<Class<T>> type() {
        return Optional.ofNullable(type);
    }

    @Override
    public Optional<GenericType<T>> genericType() {
        return Optional.ofNullable(genericType);
    }

    @Override
    public Consumer<SseMessage<T>> callback() {
        return callback;
    }
}

