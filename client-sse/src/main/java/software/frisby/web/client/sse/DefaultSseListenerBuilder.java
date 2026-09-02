package software.frisby.web.client.sse;

import software.frisby.core.validation.*;
import software.frisby.web.client.Client;
import software.frisby.web.client.PathParameter;
import software.frisby.web.client.RetryDelay;
import software.frisby.web.client.SseSpec;
import software.frisby.web.client.security.SecurityProvider;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Package-private implementation of {@link SseListenerBuilder}.
 * <p>
 * Navigation calls (path/parameter/header/cookie/security) are recorded as replayable
 * operations in {@link #navigationOps} <em>and</em>, once {@link #client(Client)} has
 * been called, immediately applied to {@link #navigationTemplate} — a throwaway
 * {@code SseSpec} obtained from the client solely so every navigation method can reuse
 * {@code SseSpec}'s own validation (including client-managed-header rejection and path
 * placeholder checks) rather than duplicating it here. Because {@link #client(Client)}
 * may be called at any point in the fluent chain, navigation calls made before it are
 * simply queued; {@link #client(Client)} replays them all against the newly created
 * template the moment it is called, so validation still happens exactly once per
 * operation, just potentially deferred to that point instead of the original call site.
 * {@code navigationTemplate} itself is never used to send a request; only
 * {@link #navigationOps} is handed to the constructed {@link DefaultSseListener}, which
 * replays it against a fresh {@code client.sse()} on every connection attempt.
 */
final class DefaultSseListenerBuilder implements SseListenerBuilder {
    private static final String CLIENT_ARGUMENT_NAME = "client";
    private static final String EVENT_ARGUMENT_NAME = "event";
    private static final String HANDLER_ARGUMENT_NAME = "handler";
    private static final String ID_ARGUMENT_NAME = "id";
    private static final String POLICY_ARGUMENT_NAME = "policy";
    private static final String EXECUTOR_ARGUMENT_NAME = "executor";
    private static final String STRATEGY_ARGUMENT_NAME = "strategy";
    private static final String TIMEOUT_ARGUMENT_NAME = "timeout";

    private static final RetryDelay DEFAULT_RECONNECT_DELAY = RetryDelay.exponential(Duration.ofSeconds(3));
    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    private final List<Consumer<SseSpec>> navigationOps;
    private final Map<String, SseHandler> eventHandlers;
    private final Map<String, SseBatchHandler> batchHandlers;

    private Client client;
    private SseSpec navigationTemplate;
    private String lastEventId;
    private BufferFullPolicy bufferFullPolicy;
    private Consumer<SseMessage<String>> droppedHandler;
    private ExecutorService executor;
    private SseHandler unhandledHandler;
    private SseBatchHandler unhandledBatchHandler;
    private Consumer<SseErrorEvent> errorHandler;
    private RetryDelay reconnectDelay;
    private Duration closeTimeout;

    DefaultSseListenerBuilder() {
        this.navigationOps = new ArrayList<>();
        this.eventHandlers = new LinkedHashMap<>();
        this.batchHandlers = new LinkedHashMap<>();
        this.client = null;
        this.navigationTemplate = null;
        this.lastEventId = null;
        this.bufferFullPolicy = BufferFullPolicy.BLOCK;
        this.droppedHandler = null;
        this.executor = null;
        this.unhandledHandler = null;
        this.unhandledBatchHandler = null;
        this.errorHandler = null;
        this.reconnectDelay = null;
        this.closeTimeout = null;
    }

    @Override
    public SseListenerBuilder client(Client client) {
        this.client = Values.notNull(CLIENT_ARGUMENT_NAME, client);
        this.navigationTemplate = this.client.sse();

        for (Consumer<SseSpec> operation : navigationOps) {
            operation.accept(navigationTemplate);
        }

        return this;
    }

    private SseListenerBuilder applyNavigation(Consumer<SseSpec> operation) {
        if (null != navigationTemplate) {
            operation.accept(navigationTemplate);
        }

        navigationOps.add(operation);
        return this;
    }

    /**
     * Rejects a registration for {@code event} if it is already registered via either
     * {@link #onEvent(String, SseHandler)} or {@link #onEvent(String, SseBatchHandler)}
     * — the two maps are populated independently, so without this check a later call
     * silently shadows an earlier one (same map) or resolves ambiguously at dispatch
     * time (across maps, inside {@link DefaultSseListener}'s pipeline construction)
     * instead of failing fast here.
     */
    private void checkEventNotRegistered(String event) {
        if (eventHandlers.containsKey(event) || batchHandlers.containsKey(event)) {
            throw new DuplicateElementsException(
                    "The '" + EVENT_ARGUMENT_NAME + "' value is invalid.  An event named '" + event
                            + "' is already registered via onEvent."
            );
        }
    }

    @Override
    public SseListenerBuilder path(String path) {
        return applyNavigation(spec -> spec.path(path));
    }

    @Override
    public SseListenerBuilder path(String path, String parameterId, String parameterValue) {
        return applyNavigation(spec -> spec.path(path, parameterId, parameterValue));
    }

    @Override
    public SseListenerBuilder path(String path, PathParameter... parameters) {
        return applyNavigation(spec -> spec.path(path, parameters));
    }

    @Override
    public SseListenerBuilder parameter(String name, String value) {
        return applyNavigation(spec -> spec.parameter(name, value));
    }

    @Override
    public SseListenerBuilder parameter(String name, String... values) {
        return applyNavigation(spec -> spec.parameter(name, values));
    }

    @Override
    public SseListenerBuilder header(String name, String value) {
        return applyNavigation(spec -> spec.header(name, value));
    }

    @Override
    public SseListenerBuilder header(String name, String... values) {
        return applyNavigation(spec -> spec.header(name, values));
    }

    @Override
    public SseListenerBuilder cookie(HttpCookie cookie) {
        return applyNavigation(spec -> spec.cookie(cookie));
    }

    @Override
    public SseListenerBuilder security(SecurityProvider provider) {
        return applyNavigation(spec -> spec.security(provider));
    }

    @Override
    public SseListenerBuilder lastEventId(String id) {
        this.lastEventId = Strings.notBlank(ID_ARGUMENT_NAME, id);
        return this;
    }

    @Override
    public SseListenerBuilder onBufferFull(BufferFullPolicy policy) {
        this.bufferFullPolicy = Values.notNull(POLICY_ARGUMENT_NAME, policy);
        return this;
    }

    @Override
    public SseListenerBuilder onDropped(Consumer<SseMessage<String>> handler) {
        this.droppedHandler = Values.notNull(HANDLER_ARGUMENT_NAME, handler);
        return this;
    }

    @Override
    public SseListenerBuilder executor(ExecutorService executor) {
        this.executor = Values.notNull(EXECUTOR_ARGUMENT_NAME, executor);
        return this;
    }

    @Override
    public SseListenerBuilder onEvent(String event, SseHandler handler) {
        Strings.notBlank(EVENT_ARGUMENT_NAME, event);
        Values.notNull(HANDLER_ARGUMENT_NAME, handler);
        checkEventNotRegistered(event);

        eventHandlers.put(event, handler);
        return this;
    }

    @Override
    public SseListenerBuilder onUnhandledEvent(Consumer<SseMessage<String>> handler) {
        Values.notNull(HANDLER_ARGUMENT_NAME, handler);
        this.unhandledHandler = SseHandler.of(handler);
        this.unhandledBatchHandler = null;
        return this;
    }

    @Override
    public SseListenerBuilder onUnhandledEvent(SseHandler handler) {
        Values.notNull(HANDLER_ARGUMENT_NAME, handler);

        if (handler.type().isPresent() || handler.genericType().isPresent()) {
            throw new IllegalArgumentException(
                    "The '" + HANDLER_ARGUMENT_NAME + "' value is invalid.  An onUnhandledEvent handler must be "
                            + "raw; it cannot declare a type() or genericType()."
            );
        }

        this.unhandledHandler = handler;
        this.unhandledBatchHandler = null;
        return this;
    }

    @Override
    public SseListenerBuilder onUnhandledEvent(SseBatchHandler handler) {
        Values.notNull(HANDLER_ARGUMENT_NAME, handler);

        if (handler.type().isPresent() || handler.genericType().isPresent()) {
            throw new IllegalArgumentException(
                    "The '" + HANDLER_ARGUMENT_NAME + "' value is invalid.  An onUnhandledEvent handler must be "
                            + "raw; it cannot declare a type() or genericType()."
            );
        }

        this.unhandledBatchHandler = handler;
        this.unhandledHandler = null;
        return this;
    }

    @Override
    public SseListenerBuilder onEvent(String event, SseBatchHandler handler) {
        Strings.notBlank(EVENT_ARGUMENT_NAME, event);
        Values.notNull(HANDLER_ARGUMENT_NAME, handler);
        checkEventNotRegistered(event);

        batchHandlers.put(event, handler);
        return this;
    }

    @Override
    public SseListenerBuilder onError(Consumer<SseErrorEvent> handler) {
        this.errorHandler = Values.notNull(HANDLER_ARGUMENT_NAME, handler);
        return this;
    }

    @Override
    public SseListenerBuilder reconnectDelay(RetryDelay strategy) {
        this.reconnectDelay = Values.notNull(STRATEGY_ARGUMENT_NAME, strategy);
        return this;
    }

    @Override
    public SseListenerBuilder closeTimeout(Duration timeout) {
        this.closeTimeout = Durations.positive(TIMEOUT_ARGUMENT_NAME, timeout);
        return this;
    }

    @Override
    public SseListener build() {
        Values.notNull(CLIENT_ARGUMENT_NAME, client);

        if (eventHandlers.isEmpty()
                && batchHandlers.isEmpty()
                && null == unhandledHandler
                && null == unhandledBatchHandler) {
            throw new IllegalStateException(
                    "The 'builder' value is invalid.  At least one handler must be registered via onEvent or "
                            + "onUnhandledEvent (or their batch equivalents) before calling build()."
            );
        }

        return new DefaultSseListener(
                client,
                List.copyOf(navigationOps),
                lastEventId,
                bufferFullPolicy,
                droppedHandler,
                executor,
                Map.copyOf(eventHandlers),
                Map.copyOf(batchHandlers),
                unhandledHandler,
                unhandledBatchHandler,
                errorHandler,
                null != reconnectDelay ? reconnectDelay : DEFAULT_RECONNECT_DELAY,
                null != closeTimeout ? closeTimeout : DEFAULT_CLOSE_TIMEOUT
        );
    }
}



