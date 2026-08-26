package software.frisby.web.client.sse;

import software.frisby.core.concurrency.NamedExecutorService;
import software.frisby.core.concurrency.fluent.*;
import software.frisby.web.client.Client;
import software.frisby.web.client.Headers;
import software.frisby.web.client.RetryDelay;
import software.frisby.web.client.SseSpec;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Package-private implementation of {@link SseListener}.
 * <p>
 * Owns the reader thread, one dispatch pipeline per registered {@code onEvent}/
 * {@code onEventBatch} handler (built once in {@link #connectAsync()}, torn down only in
 * {@link #close()}), and the listener's owned executor, if one was created because the
 * caller did not supply their own via {@link SseListenerBuilder#executor}.
 * <p>
 * Every handler's pipeline starts the same way — {@code Buffer<RawSseEvent> →
 * Transform<RawSseEvent, SseMessage>} (deserializing, or passing the raw {@code data}
 * string through unchanged for a raw handler) — a failed deserialization is caught inside
 * the {@code Transform} step, logged at {@code WARNING}, routed to {@code onError}, and
 * dropped ({@code null} result) rather than propagated. An {@code onEvent} handler's
 * pipeline ends there with a terminal {@code Action}; an {@code onEventBatch} handler's
 * pipeline inserts an additional {@code Batch<SseMessage>} stage before the terminal
 * {@code Action}, so batching only ever groups already-deserialized, already-valid
 * messages — a failed item is simply absent from the batch it would have belonged to,
 * rather than requiring special partial-batch-failure handling. {@code concurrency > 1}
 * wraps either shape in a {@code Router} with that many arms (see
 * {@link #buildHandlerPipeline} / {@link #buildBatchHandlerPipeline}).
 * <p>
 * <strong>Reconnect loop not yet implemented.</strong> {@code reconnectDelay} is accepted
 * and stored now so {@link DefaultSseListenerBuilder} has a stable constructor to call,
 * but is not yet acted on here — this lands in Chunk 9. A connect or read failure is
 * logged at {@code Error}, routed to the registered {@code onError} handler if set, and
 * simply ends the reader thread without retrying.
 */
final class DefaultSseListener implements SseListener {
    private static final System.Logger LOGGER = System.getLogger(DefaultSseListener.class.getName());
    private static final String DEFAULT_EVENT_TYPE = "message";
    private static final String EXECUTOR_THREAD_PREFIX = "sse-listener";
    private static final String READER_THREAD_NAME = "sse-listener-reader";

    private final Client client;
    private final List<Consumer<SseSpec>> navigationOps;
    private final String initialLastEventId;
    private final int bufferCapacity;
    private final BufferFullPolicy bufferFullPolicy;
    private final Executor callerExecutor;
    private final Map<String, SseHandler> eventHandlers;
    private final Map<String, SseHandler> batchHandlers;
    private final Consumer<SseMessage<String>> unhandledHandler;
    private final int batchSize;
    private final Duration batchTimeout;
    private final Consumer<Throwable> errorHandler;
    private final RetryDelay reconnectDelay;

    private final AtomicBoolean started;
    private final AtomicBoolean closed;
    private final AtomicReference<InputStream> currentStream;

    private Executor pipelineExecutor;
    private NamedExecutorService ownedExecutor;
    private Map<String, Pipeline<RawSseEvent>> handlerPipelines;
    private Pipeline<RawSseEvent> unhandledPipeline;
    private Thread readerThread;

    DefaultSseListener(Client client,
                       List<Consumer<SseSpec>> navigationOps,
                       String initialLastEventId,
                       int bufferCapacity,
                       BufferFullPolicy bufferFullPolicy,
                       Executor callerExecutor,
                       Map<String, SseHandler> eventHandlers,
                       Map<String, SseHandler> batchHandlers,
                       Consumer<SseMessage<String>> unhandledHandler,
                       int batchSize,
                       Duration batchTimeout,
                       Consumer<Throwable> errorHandler,
                       RetryDelay reconnectDelay) {
        this.client = client;
        this.navigationOps = navigationOps;
        this.initialLastEventId = initialLastEventId;
        this.bufferCapacity = bufferCapacity;
        this.bufferFullPolicy = bufferFullPolicy;
        this.callerExecutor = callerExecutor;
        this.eventHandlers = eventHandlers;
        this.batchHandlers = batchHandlers;
        this.unhandledHandler = unhandledHandler;
        this.batchSize = batchSize;
        this.batchTimeout = batchTimeout;
        this.errorHandler = errorHandler;
        this.reconnectDelay = reconnectDelay;
        this.started = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.currentStream = new AtomicReference<>();
        this.pipelineExecutor = null;
        this.ownedExecutor = null;
        this.handlerPipelines = null;
        this.unhandledPipeline = null;
        this.readerThread = null;
    }

    @Override
    public void connectAsync() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        if (null != callerExecutor) {
            pipelineExecutor = callerExecutor;
        } else {
            ownedExecutor = NamedExecutorService.builder()
                    .threadPrefix(EXECUTOR_THREAD_PREFIX)
                    .build();
            pipelineExecutor = ownedExecutor;
        }

        handlerPipelines = buildHandlerPipelines();
        unhandledPipeline = buildUnhandledPipeline();

        readerThread = new Thread(
                new ReaderTask(
                        client,
                        navigationOps,
                        initialLastEventId,
                        handlerPipelines,
                        unhandledPipeline,
                        errorHandler,
                        currentStream
                ),
                READER_THREAD_NAME
        );
        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public boolean isOpen() {
        return started.get() && !closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        InputStream stream = currentStream.getAndSet(null);

        if (null != stream) {
            try {
                stream.close();
            } catch (IOException e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to close the SSE input stream.", e);
            }
        }

        if (null != readerThread) {
            readerThread.interrupt();
        }

        if (null != handlerPipelines) {
            handlerPipelines.values().forEach(Pipeline::complete);
        }

        if (null != unhandledPipeline) {
            unhandledPipeline.complete();
        }

        if (null != handlerPipelines) {
            handlerPipelines.values().forEach(Pipeline::awaitCompletion);
        }

        if (null != unhandledPipeline) {
            unhandledPipeline.awaitCompletion();
        }

        if (null != ownedExecutor) {
            ownedExecutor.shutdown();
        }
    }

    private Map<String, Pipeline<RawSseEvent>> buildHandlerPipelines() {
        Map<String, Pipeline<RawSseEvent>> pipelines = new HashMap<>();

        for (Map.Entry<String, SseHandler> entry : eventHandlers.entrySet()) {
            pipelines.put(entry.getKey(), buildHandlerPipeline(entry.getValue()));
        }

        // Batch registrations are applied after single-event ones, so an event type
        // registered via both onEvent and onEventBatch resolves to its batch pipeline —
        // an edge case neither builder rejects, but not an expected usage pattern.
        for (Map.Entry<String, SseHandler> entry : batchHandlers.entrySet()) {
            pipelines.put(entry.getKey(), buildBatchHandlerPipeline(entry.getValue()));
        }

        return pipelines;
    }

    private Pipeline<RawSseEvent> buildHandlerPipeline(SseHandler handler) {
        if (1 == handler.concurrency()) {
            return Pipeline.<RawSseEvent>builder()
                    .executor(pipelineExecutor)
                    .from(Buffer.of(RawSseEvent.class).capacity(bufferCapacity))
                    .then(Transform.of(RawSseEvent.class, SseMessage.class)
                            .transform(raw -> deserializeSafely(raw, handler)))
                    .to(message -> dispatchSafely(handler, message));
        }

        return Pipeline.<RawSseEvent>builder()
                .executor(pipelineExecutor)
                .from(Router.of(RawSseEvent.class)
                        .routes(handler.concurrency())
                        .factory(() -> Pipeline.<RawSseEvent>builder()
                                .executor(pipelineExecutor)
                                .from(Buffer.of(RawSseEvent.class).capacity(bufferCapacity))
                                .then(Transform.of(RawSseEvent.class, SseMessage.class)
                                        .transform(raw -> deserializeSafely(raw, handler)))
                                .to(message -> dispatchSafely(handler, message))));
    }

    private Pipeline<RawSseEvent> buildBatchHandlerPipeline(SseHandler handler) {
        if (1 == handler.concurrency()) {
            return Pipeline.<RawSseEvent>builder()
                    .executor(pipelineExecutor)
                    .from(Buffer.of(RawSseEvent.class).capacity(bufferCapacity))
                    .then(Transform.of(RawSseEvent.class, SseMessage.class)
                            .transform(raw -> deserializeSafely(raw, handler)))
                    .then(Batch.of(SseMessage.class)
                            .batchSize(batchSize)
                            .timeout(batchTimeout))
                    .to(messages -> dispatchBatchSafely(handler, messages));
        }

        return Pipeline.<RawSseEvent>builder()
                .executor(pipelineExecutor)
                .from(Router.of(RawSseEvent.class)
                        .routes(handler.concurrency())
                        .factory(() -> Pipeline.<RawSseEvent>builder()
                                .executor(pipelineExecutor)
                                .from(Buffer.of(RawSseEvent.class).capacity(bufferCapacity))
                                .then(Transform.of(RawSseEvent.class, SseMessage.class)
                                        .transform(raw -> deserializeSafely(raw, handler)))
                                .then(Batch.of(SseMessage.class)
                                        .batchSize(batchSize)
                                        .timeout(batchTimeout))
                                .to(messages -> dispatchBatchSafely(handler, messages))));
    }

    private Pipeline<RawSseEvent> buildUnhandledPipeline() {
        return Pipeline.<RawSseEvent>builder()
                .executor(pipelineExecutor)
                .from(Buffer.of(RawSseEvent.class).capacity(bufferCapacity))
                .then(Transform.of(RawSseEvent.class, SseMessage.class)
                        .transform(this::toRawMessage))
                .to(this::dispatchUnhandledSafely);
    }

    private SseMessage<?> deserializeSafely(RawSseEvent raw, SseHandler handler) {
        try {
            return toMessage(raw, handler);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "SSE event deserialization failed.", e);
            notifyError(e);
            return null;
        }
    }

    private SseMessage<?> toMessage(RawSseEvent raw, SseHandler handler) {
        String resolvedEvent = raw.event().orElse(DEFAULT_EVENT_TYPE);
        Object body;

        if (null != handler.type()) {
            body = client.configuration().serializer()
                    .deserialize(raw.data().getBytes(StandardCharsets.UTF_8), handler.type());
        } else if (null != handler.genericType()) {
            body = client.configuration().serializer()
                    .deserialize(raw.data().getBytes(StandardCharsets.UTF_8), handler.genericType());
        } else {
            body = raw.data();
        }

        return new DefaultSseMessage<>(raw.id(), resolvedEvent, body, Instant.now());
    }

    private SseMessage<String> toRawMessage(RawSseEvent raw) {
        return new DefaultSseMessage<>(raw.id(), raw.event().orElse(DEFAULT_EVENT_TYPE), raw.data(), Instant.now());
    }

    private void dispatchSafely(SseHandler handler, SseMessage<?> message) {
        if (null == message) {
            return;
        }

        try {
            invokeCallback(handler.callback(), message);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "SSE handler callback failed.", e);
            notifyError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchUnhandledSafely(SseMessage<?> message) {
        if (null == message || null == unhandledHandler) {
            return;
        }

        try {
            unhandledHandler.accept((SseMessage<String>) message);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "SSE unhandled-event callback failed.", e);
            notifyError(e);
        }
    }

    private void dispatchBatchSafely(SseHandler handler, List<?> messages) {
        if (null == messages || messages.isEmpty()) {
            return;
        }

        try {
            invokeBatchCallback(handler.callback(), messages);
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "SSE batch handler callback failed.", e);
            notifyError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void invokeBatchCallback(Consumer<?> callback, List<?> messages) {
        ((Consumer<List<?>>) callback).accept(messages);
    }

    @SuppressWarnings("unchecked")
    private void invokeCallback(Consumer<?> callback, SseMessage<?> message) {
        ((Consumer<SseMessage<?>>) callback).accept(message);
    }

    private void notifyError(Throwable error) {
        if (null == errorHandler) {
            return;
        }

        try {
            errorHandler.accept(error);
        } catch (Exception ex) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "The SSE onError handler threw an unexpected exception.",
                    ex
            );
        }
    }

    /**
     * Reads {@code text/event-stream} bytes off the wire and posts each assembled event to
     * the appropriate handler pipeline.
     * <p>
     * Declared {@code private static} with every dependency passed via the constructor,
     * per the project's "Worker / Runnable nested classes" style rule.
     */
    private static final class ReaderTask implements Runnable {
        private final Client client;
        private final List<Consumer<SseSpec>> navigationOps;
        private final String initialLastEventId;
        private final Map<String, Pipeline<RawSseEvent>> handlerPipelines;
        private final Pipeline<RawSseEvent> unhandledPipeline;
        private final Consumer<Throwable> errorHandler;
        private final AtomicReference<InputStream> currentStream;

        private ReaderTask(Client client,
                           List<Consumer<SseSpec>> navigationOps,
                           String initialLastEventId,
                           Map<String, Pipeline<RawSseEvent>> handlerPipelines,
                           Pipeline<RawSseEvent> unhandledPipeline,
                           Consumer<Throwable> errorHandler,
                           AtomicReference<InputStream> currentStream) {
            this.client = client;
            this.navigationOps = navigationOps;
            this.initialLastEventId = initialLastEventId;
            this.handlerPipelines = handlerPipelines;
            this.unhandledPipeline = unhandledPipeline;
            this.errorHandler = errorHandler;
            this.currentStream = currentStream;
        }

        @Override
        public void run() {
            try (InputStream in = openStream()) {
                currentStream.set(in);

                SseEventParser parser = new SseEventParser(in);
                Optional<RawSseEvent> event;

                while ((event = parser.next()).isPresent()) {
                    dispatch(event.get());
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.log(System.Logger.Level.ERROR, "The SSE connection failed.", e);

                notifyError(errorHandler, e);
            } finally {
                currentStream.set(null);
            }
        }

        private static void notifyError(Consumer<Throwable> errorHandler, Throwable error) {
            if (null == errorHandler) {
                return;
            }

            try {
                errorHandler.accept(error);
            } catch (Exception ex) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "The SSE onError handler threw an unexpected exception.",
                        ex
                );
            }
        }

        private InputStream openStream() {
            SseSpec spec = client.sse();

            for (Consumer<SseSpec> operation : navigationOps) {
                operation.accept(spec);
            }

            if (null != initialLastEventId) {
                spec.header(Headers.LAST_EVENT_ID, initialLastEventId);
            }

            HttpResponse<InputStream> response = spec.stream();
            return response.body();
        }

        private void dispatch(RawSseEvent raw) {
            String eventType = raw.event().orElse(DEFAULT_EVENT_TYPE);
            Pipeline<RawSseEvent> target = handlerPipelines.getOrDefault(eventType, unhandledPipeline);
            target.post(raw);
        }
    }
}







