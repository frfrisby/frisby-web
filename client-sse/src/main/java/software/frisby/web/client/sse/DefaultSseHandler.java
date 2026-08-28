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
final class DefaultSseHandler implements SseHandler {
    private static final String TYPE_ARGUMENT_NAME = "type";
    private static final String HANDLER_ARGUMENT_NAME = "handler";
    private static final String CAPACITY_ARGUMENT_NAME = "capacity";
    private static final String CONCURRENCY_ARGUMENT_NAME = "concurrency";

    static final int DEFAULT_CAPACITY = 1024;
    static final int DEFAULT_CONCURRENCY = 1;

    private final Class<?> type;
    private final GenericType<?> genericType;
    private final Consumer<?> callback;
    private int capacity;
    private int concurrency;

    static DefaultSseHandler ofType(Class<?> type, Consumer<?> callback) {
        Values.notNull(TYPE_ARGUMENT_NAME, type);
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseHandler(type, null, callback);
    }

    static DefaultSseHandler ofGenericType(GenericType<?> type, Consumer<?> callback) {
        Values.notNull(TYPE_ARGUMENT_NAME, type);
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseHandler(null, type, callback);
    }

    static DefaultSseHandler ofRaw(Consumer<?> callback) {
        Values.notNull(HANDLER_ARGUMENT_NAME, callback);
        return new DefaultSseHandler(null, null, callback);
    }

    private DefaultSseHandler(Class<?> type, GenericType<?> genericType, Consumer<?> callback) {
        this.type = type;
        this.genericType = genericType;
        this.callback = callback;
        this.capacity = DEFAULT_CAPACITY;
        this.concurrency = DEFAULT_CONCURRENCY;
    }

    @Override
    public SseHandler capacity(int capacity) {
        this.capacity = Numbers.positive(CAPACITY_ARGUMENT_NAME, capacity);
        return this;
    }

    @Override
    public SseHandler concurrency(int concurrency) {
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

