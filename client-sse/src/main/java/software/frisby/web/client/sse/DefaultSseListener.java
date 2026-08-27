package software.frisby.web.client.sse;

import software.frisby.core.concurrency.GenericType;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Package-private implementation of {@link SseListener}.
 * <p>
 * Owns the reader thread, one dispatch pipeline per registered {@code onEvent}/
 * {@code onEventBatch} handler (built once in {@link #connectAsync()}, torn down only in
 * {@link #close()}), and the listener's owned executor, if one was created because the
 * caller did not supply their own via {@link SseListenerBuilder#executor}.
 * <p>
 * Every handler's pipeline starts the same way — {@code Buffer<RawSseEvent> →
 * Transform<RawSseEvent, Delivery>} (deserializing, or passing the raw {@code data}
 * string through unchanged for a raw handler; {@link Delivery} pairs the resulting
 * {@link SseMessage} with the {@link RawSseEvent} it came from, so a handler callback
 * exception can still report the untouched wire-format {@code data} string as
 * {@link SseErrorEvent} context) — a failed deserialization is caught inside the
 * {@code Transform} step, logged at {@code WARNING}, routed to {@code onError} with that
 * same raw context, and dropped ({@code null} result) rather than propagated. An
 * {@code onEvent} handler's pipeline ends there with a terminal {@code Action}; an
 * {@code onEventBatch} handler's pipeline uses {@code Batch<RawSseEvent>} as its own head
 * stage instead of {@code Buffer<RawSseEvent>} — {@code Batch} already has its own
 * internal queue/{@code capacity} and dedicated worker thread (see {@code concurrency.md}),
 * so chaining a separate {@code Buffer} in front of it would cost a second thread per
 * batch handler for no benefit — followed by a
 * {@code Transform<List<RawSseEvent>, List<Delivery>>} that deserializes each item in the
 * batch individually via {@link #deserializeBatchSafely}: a single item's failure is
 * caught and that item alone is filtered out of the resulting list, never the whole
 * batch (see {@link #deserializeSafely}, reused per-item here). {@code concurrency > 1}
 * wraps either shape in a {@code Router} with that many arms (see
 * {@link #buildHandlerPipeline} / {@link #buildBatchHandlerPipeline}).
 * <p>
 * <strong>Dispatch resolution.</strong> An event whose {@code event} field is explicitly
 * present is looked up in the per-event-type handler map by that exact value — including
 * the literal string {@code "message"}, if a handler happens to be registered for it. An
 * event with <em>no</em> {@code event} field at all is never looked up in that map; it is
 * always routed straight to the unhandled-event pipeline. This keeps "the producer didn't
 * set an event type" distinct from "the producer explicitly chose the name message" —
 * conflating the two would let an untyped event be silently deserialized against whatever
 * type a caller happened to register under {@code "message"}. See {@link ReaderTask#dispatch}.
 * <p>
 * <strong>Reconnect loop.</strong> On any stream close — a clean end-of-stream from the
 * server, an I/O failure, or a policy-driven {@link BufferFullPolicy#DISCONNECT} — the
 * reader thread reconnects unconditionally and indefinitely, re-invoking
 * {@code client.sse()...stream()} (replaying the stored navigation template) with
 * {@code header(Headers.LAST_EVENT_ID, ...)} set to the most recently parsed event's
 * {@code id}, if any. Only an actual failure (an {@link IOException} or unexpected
 * {@link RuntimeException} — not a clean end-of-stream, and not a policy-driven
 * disconnect) is logged at {@code Error} and routed to the registered {@code onError}
 * handler; a clean close or a deliberate {@code DISCONNECT} reconnects silently. The
 * delay before each reconnect attempt is the server's most recently received
 * {@code retry} field, if any (consumed for one attempt only), otherwise the configured
 * {@link SseListenerBuilder#reconnectDelay(RetryDelay) reconnectDelay} strategy, whose
 * attempt counter resets to zero on every successful connection and increments only on
 * an actual failure. The only way this loop ever stops is {@link #close()}.
 * <p>
 * <strong>{@link BufferFullPolicy}.</strong> Every dispatch first checks
 * {@code target.inFlight()} against {@code bufferCapacity} before posting — required
 * because {@code Buffer}'s own {@code post()} has no non-blocking or reject-on-full
 * mode (see {@code concurrency.md}). {@code BLOCK} skips this check entirely and posts
 * unconditionally, relying on {@code Buffer}'s natural blocking behavior. {@code DROP}
 * silently discards the event once the threshold is reached — logging a {@code WARNING}
 * when a run of drops begins and another when the buffer recovers (with a summary count
 * and duration), rather than one {@code WARNING} per dropped event, which would flood
 * logs under the exact sustained-high-volume conditions {@code DROP} is meant for; if
 * the connection itself ends (clean EOF, an error, or {@link #close()}) while a drop
 * episode is still in progress, the same summary is logged at that point instead, since
 * a recovery may never otherwise occur. The caller's
 * {@link SseListenerBuilder#onDropped onDropped} handler, if registered, still fires
 * once per dropped event for callers who want per-item granularity (e.g. a metrics
 * counter). {@code DISCONNECT} closes the current stream instead of posting, triggering
 * the reconnect loop above with the last successfully parsed {@code id} already recorded
 * for {@code Last-Event-ID} replay.
 */
final class DefaultSseListener implements SseListener {
    private static final System.Logger LOGGER = System.getLogger(DefaultSseListener.class.getName());
    private static final String EXECUTOR_THREAD_PREFIX = "sse-listener";
    private static final String READER_THREAD_NAME = "sse-listener-reader";

    private final Client client;
    private final List<Consumer<SseSpec>> navigationOps;
    private final String initialLastEventId;
    private final int bufferCapacity;
    private final BufferFullPolicy bufferFullPolicy;
    private final Consumer<SseMessage<String>> droppedHandler;
    private final Executor callerExecutor;
    private final Map<String, SseHandler> eventHandlers;
    private final Map<String, SseHandler> batchHandlers;
    private final Consumer<SseMessage<String>> unhandledHandler;
    private final int batchSize;
    private final Duration batchTimeout;
    private final Consumer<SseErrorEvent> errorHandler;
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
                       Consumer<SseMessage<String>> droppedHandler,
                       Executor callerExecutor,
                       Map<String, SseHandler> eventHandlers,
                       Map<String, SseHandler> batchHandlers,
                       Consumer<SseMessage<String>> unhandledHandler,
                       int batchSize,
                       Duration batchTimeout,
                       Consumer<SseErrorEvent> errorHandler,
                       RetryDelay reconnectDelay) {
        this.client = client;
        this.navigationOps = navigationOps;
        this.initialLastEventId = initialLastEventId;
        this.bufferCapacity = bufferCapacity;
        this.bufferFullPolicy = bufferFullPolicy;
        this.droppedHandler = droppedHandler;
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
                        currentStream,
                        bufferCapacity,
                        bufferFullPolicy,
                        droppedHandler,
                        this::toRawMessage,
                        reconnectDelay,
                        closed
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
                    .from(Buffer.of(RawSseEvent.class)
                            .capacity(bufferCapacity))
                    .then(Transform.of(RawSseEvent.class, Delivery.class)
                            .transform(raw -> deserializeSafely(raw, handler)))
                    .to(delivery -> dispatchSafely(handler, delivery));
        }

        return Pipeline.<RawSseEvent>builder()
                .executor(pipelineExecutor)
                .from(Router.of(RawSseEvent.class)
                        .routes(handler.concurrency())
                        .factory(() -> Pipeline.<RawSseEvent>builder()
                                .executor(pipelineExecutor)
                                .from(Buffer.of(RawSseEvent.class)
                                        .capacity(bufferCapacity))
                                .then(Transform.of(RawSseEvent.class, Delivery.class)
                                        .transform(raw -> deserializeSafely(raw, handler)))
                                .to(delivery -> dispatchSafely(handler, delivery))));
    }

    private Pipeline<RawSseEvent> buildBatchHandlerPipeline(SseHandler handler) {
        if (1 == handler.concurrency()) {
            return Pipeline.<RawSseEvent>builder()
                    .executor(pipelineExecutor)
                    .from(Batch.of(RawSseEvent.class)
                            .capacity(bufferCapacity)
                            .batchSize(batchSize)
                            .timeout(batchTimeout))
                    .then(Transform.of(
                                    new GenericType<List<RawSseEvent>>() {
                                    },
                                    new GenericType<List<Delivery>>() {
                                    })
                            .transform(raws -> deserializeBatchSafely(raws, handler)))
                    .to(deliveries -> dispatchBatchSafely(handler, deliveries));
        }

        return Pipeline.<RawSseEvent>builder()
                .executor(pipelineExecutor)
                .from(Router.of(RawSseEvent.class)
                        .routes(handler.concurrency())
                        .factory(() -> Pipeline.<RawSseEvent>builder()
                                .executor(pipelineExecutor)
                                .from(Batch.of(RawSseEvent.class)
                                        .capacity(bufferCapacity)
                                        .batchSize(batchSize)
                                        .timeout(batchTimeout))
                                .then(Transform.of(
                                                new GenericType<List<RawSseEvent>>() {
                                                },
                                                new GenericType<List<Delivery>>() {
                                                })
                                        .transform(raws -> deserializeBatchSafely(raws, handler)))
                                .to(deliveries -> dispatchBatchSafely(handler, deliveries))));
    }

    private Pipeline<RawSseEvent> buildUnhandledPipeline() {
        return Pipeline.<RawSseEvent>builder()
                .executor(pipelineExecutor)
                .from(Buffer.of(RawSseEvent.class)
                        .capacity(bufferCapacity))
                .then(Transform.of(RawSseEvent.class, SseMessage.class)
                        .transform(this::toRawMessage))
                .to(this::dispatchUnhandledSafely);
    }

    private Delivery deserializeSafely(RawSseEvent raw, SseHandler handler) {
        try {
            return new Delivery(raw, toMessage(raw, handler));
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "SSE event deserialization failed.", e);
            notifyError(toRawMessage(raw), e);
            return null;
        }
    }

    /**
     * Deserializes each item in a batch individually, filtering out any item whose
     * deserialization fails ({@link #deserializeSafely}) rather than discarding the whole
     * batch — a single malformed event should never take an entire batch's worth of good
     * events down with it.
     */
    private List<Delivery> deserializeBatchSafely(List<RawSseEvent> raws, SseHandler handler) {
        List<Delivery> deliveries = new ArrayList<>(raws.size());

        for (RawSseEvent raw : raws) {
            Delivery delivery = deserializeSafely(raw, handler);

            if (null != delivery) {
                deliveries.add(delivery);
            }
        }

        return deliveries;
    }

    private SseMessage<?> toMessage(RawSseEvent raw, SseHandler handler) {
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

        return new DefaultSseMessage<>(raw.id(), raw.event(), body, Instant.now());
    }

    private SseMessage<String> toRawMessage(RawSseEvent raw) {
        return new DefaultSseMessage<>(raw.id(), raw.event(), raw.data(), Instant.now());
    }

    private void dispatchSafely(SseHandler handler, Delivery delivery) {
        if (null == delivery) {
            return;
        }

        try {
            invokeCallback(handler.callback(), delivery.message());
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "SSE handler callback failed.", e);
            notifyError(toRawMessage(delivery.raw()), e);
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
            notifyError((SseMessage<String>) message, e);
        }
    }

    private void dispatchBatchSafely(SseHandler handler, List<Delivery> deliveries) {
        if (null == deliveries || deliveries.isEmpty()) {
            return;
        }

        List<?> messages = deliveries.stream()
                .map(Delivery::message)
                .toList();

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
        notify(SseErrorEvent.of(error));
    }

    private void notifyError(SseMessage<String> rawMessage, Throwable error) {
        notify(SseErrorEvent.of(rawMessage, error));
    }

    private void notify(SseErrorEvent event) {
        if (null == errorHandler) {
            return;
        }

        try {
            errorHandler.accept(event);
        } catch (Exception ex) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "The SSE onError handler threw an unexpected exception.",
                    ex
            );
        }
    }

    /**
     * Pairs a handler's deserialized {@link SseMessage} with the {@link RawSseEvent} it
     * was produced from, so a handler callback exception ({@link #dispatchSafely}) can
     * still report the untouched wire-format {@code data} string as {@link SseErrorEvent}
     * context — {@code message.body()} may already be a typed payload by the time the
     * callback runs, but the raw string is always available and is what {@link SseErrorEvent}
     * documents it will surface.
     */
    private record Delivery(RawSseEvent raw, SseMessage<?> message) {
    }

    /**
     * Reads {@code text/event-stream} bytes off the wire and posts each assembled event to
     * the appropriate handler pipeline; owns the reconnect loop and {@link BufferFullPolicy}
     * enforcement.
     * <p>
     * Declared {@code private static} with every dependency passed via the constructor,
     * per the project's "Worker / Runnable nested classes" style rule.
     */
    private static final class ReaderTask implements Runnable {
        private final Client client;
        private final List<Consumer<SseSpec>> navigationOps;
        private final Map<String, Pipeline<RawSseEvent>> handlerPipelines;
        private final Pipeline<RawSseEvent> unhandledPipeline;
        private final Consumer<SseErrorEvent> errorHandler;
        private final AtomicReference<InputStream> currentStream;
        private final int bufferCapacity;
        private final BufferFullPolicy bufferFullPolicy;
        private final Consumer<SseMessage<String>> droppedHandler;
        private final Function<RawSseEvent, SseMessage<String>> rawMessageFactory;
        private final RetryDelay reconnectDelay;
        private final AtomicBoolean closed;

        private final AtomicReference<String> lastEventId;
        private final AtomicReference<Duration> pendingServerRetryDelay;
        private final AtomicBoolean disconnectRequested;
        private final AtomicLong droppedCount;
        private final AtomicReference<Instant> dropEpisodeStart;

        private ReaderTask(Client client,
                           List<Consumer<SseSpec>> navigationOps,
                           String initialLastEventId,
                           Map<String, Pipeline<RawSseEvent>> handlerPipelines,
                           Pipeline<RawSseEvent> unhandledPipeline,
                           Consumer<SseErrorEvent> errorHandler,
                           AtomicReference<InputStream> currentStream,
                           int bufferCapacity,
                           BufferFullPolicy bufferFullPolicy,
                           Consumer<SseMessage<String>> droppedHandler,
                           Function<RawSseEvent, SseMessage<String>> rawMessageFactory,
                           RetryDelay reconnectDelay,
                           AtomicBoolean closed) {
            this.client = client;
            this.navigationOps = navigationOps;
            this.handlerPipelines = handlerPipelines;
            this.unhandledPipeline = unhandledPipeline;
            this.errorHandler = errorHandler;
            this.currentStream = currentStream;
            this.bufferCapacity = bufferCapacity;
            this.bufferFullPolicy = bufferFullPolicy;
            this.droppedHandler = droppedHandler;
            this.rawMessageFactory = rawMessageFactory;
            this.reconnectDelay = reconnectDelay;
            this.closed = closed;
            this.lastEventId = new AtomicReference<>(initialLastEventId);
            this.pendingServerRetryDelay = new AtomicReference<>();
            this.disconnectRequested = new AtomicBoolean(false);
            this.droppedCount = new AtomicLong(0);
            this.dropEpisodeStart = new AtomicReference<>();
        }

        @Override
        public void run() {
            int consecutiveFailures = 0;

            while (!closed.get()) {
                try (InputStream in = openStream()) {
                    currentStream.set(in);
                    consecutiveFailures = 0;
                    disconnectRequested.set(false);

                    SseEventParser parser = new SseEventParser(in);
                    Optional<RawSseEvent> event;

                    while (!closed.get() && (event = parser.next()).isPresent()) {
                        trackReconnectState(event.get());
                        dispatch(event.get());
                    }
                } catch (IOException | RuntimeException e) {
                    if (!closed.get() && !disconnectRequested.getAndSet(false)) {
                        consecutiveFailures++;
                        LOGGER.log(System.Logger.Level.ERROR, "The SSE connection failed.", e);
                        notifyError(errorHandler, e);
                    }
                } finally {
                    currentStream.set(null);
                    flushDropEpisodeAtConnectionEnd();
                }

                if (closed.get()) {
                    break;
                }

                if (!awaitReconnectDelay(consecutiveFailures)) {
                    break;
                }
            }
        }

        private void trackReconnectState(RawSseEvent raw) {
            raw.id().ifPresent(lastEventId::set);
            raw.retry().ifPresent(pendingServerRetryDelay::set);
        }

        private boolean awaitReconnectDelay(int consecutiveFailures) {
            Duration delay = resolveDelay(consecutiveFailures);

            try {
                Thread.sleep(Math.max(delay.toMillis(), 0L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            return !closed.get();
        }

        private Duration resolveDelay(int consecutiveFailures) {
            Duration serverDelay = pendingServerRetryDelay.getAndSet(null);

            if (null != serverDelay) {
                return serverDelay;
            }

            return reconnectDelay.delayFor(Math.max(consecutiveFailures, 1));
        }

        private static void notifyError(Consumer<SseErrorEvent> errorHandler, Throwable error) {
            if (null == errorHandler) {
                return;
            }

            try {
                errorHandler.accept(SseErrorEvent.of(error));
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

            String eventId = lastEventId.get();

            if (null != eventId) {
                spec.header(Headers.LAST_EVENT_ID, eventId);
            }

            HttpResponse<InputStream> response = spec.stream();
            return response.body();
        }

        /**
         * Routes an event to its handler's dedicated pipeline.
         * <p>
         * An event with no {@code event} field at all is always routed to
         * {@code unhandledPipeline} — it is never matched against a handler registered
         * for the literal string {@code "message"}. Only an event whose {@code event}
         * field is explicitly present is looked up in {@code handlerPipelines}, falling
         * back to {@code unhandledPipeline} when no handler is registered for that exact
         * value. This distinguishes "the producer didn't set an event type" from "the
         * producer explicitly chose the name message" — conflating the two would let an
         * untyped event be silently deserialized against whatever type a caller happened
         * to register under {@code "message"}.
         */
        private void dispatch(RawSseEvent raw) {
            if (raw.event().isEmpty()) {
                postWithPolicy(unhandledPipeline, raw);
                return;
            }

            Pipeline<RawSseEvent> target = handlerPipelines.getOrDefault(raw.event().get(), unhandledPipeline);
            postWithPolicy(target, raw);
        }

        /**
         * Applies {@link BufferFullPolicy} before posting. {@code Buffer}'s own
         * {@code post()} has no non-blocking or reject-on-full variant, so
         * {@code DROP}/{@code DISCONNECT} must pre-check {@code inFlight()} against
         * {@code bufferCapacity} and avoid calling {@code post()} at all once the
         * threshold is reached; {@code BLOCK} posts unconditionally and simply relies on
         * {@code Buffer}'s natural blocking behavior — it is deliberately checked first
         * and returns immediately, so it pays no cost for the drop-tracking machinery
         * below.
         */
        private void postWithPolicy(Pipeline<RawSseEvent> target, RawSseEvent raw) {
            if (BufferFullPolicy.BLOCK == bufferFullPolicy) {
                target.post(raw);
                return;
            }

            boolean hasCapacity = target.inFlight() < bufferCapacity;

            if (BufferFullPolicy.DROP == bufferFullPolicy) {
                if (hasCapacity) {
                    recordRecoveryIfNeeded();
                    target.post(raw);
                } else {
                    recordDrop(raw);
                }

                return;
            }

            if (hasCapacity) {
                target.post(raw);
                return;
            }

            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "The SSE dispatch buffer is full; disconnecting and reconnecting per "
                            + "BufferFullPolicy.DISCONNECT."
            );
            disconnectRequested.set(true);

            InputStream stream = currentStream.getAndSet(null);

            if (null != stream) {
                try {
                    stream.close();
                } catch (IOException e) {
                    LOGGER.log(
                            System.Logger.Level.WARNING,
                            "Failed to close the SSE input stream for a policy-driven disconnect.",
                            e
                    );
                }
            }
        }

        /**
         * Records one dropped event: increments the running count for the current drop
         * episode, logging a single {@code WARNING} the moment the episode begins (not
         * once per dropped event, which would flood logs under the sustained high-volume
         * conditions {@code DROP} is meant for), then notifies the caller's
         * {@link SseListenerBuilder#onDropped onDropped} handler, if registered, with the
         * raw dropped event — that handler fires for every drop, unsummarized, regardless
         * of this built-in logging.
         */
        private void recordDrop(RawSseEvent raw) {
            if (1 == droppedCount.incrementAndGet()) {
                dropEpisodeStart.set(Instant.now());
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "The SSE dispatch buffer is full; began dropping events per BufferFullPolicy.DROP."
                );
            }

            notifyDropped(raw);
        }

        /**
         * Logs a single summary {@code WARNING} — the number of events dropped and the
         * duration of the episode — the moment the buffer recovers capacity mid-stream
         * after a run of drops. A no-op when no drop is currently in progress.
         */
        private void recordRecoveryIfNeeded() {
            flushDropEpisode(
                    "The SSE dispatch buffer recovered after dropping %d event(s) over %s per BufferFullPolicy.DROP."
            );
        }

        /**
         * Logs the same drop-episode summary as {@link #recordRecoveryIfNeeded()}, but
         * for the case where the connection attempt itself ends (clean EOF, an error, or
         * an explicit {@link #close()}) while a drop episode is still in progress —
         * {@link #recordRecoveryIfNeeded()} alone would never fire in that case, since it
         * only runs when there's a subsequent event to post, and one may never arrive
         * before the stream ends. Called once per connection attempt, regardless of
         * {@link BufferFullPolicy}; a no-op when no drop is in progress.
         */
        private void flushDropEpisodeAtConnectionEnd() {
            flushDropEpisode(
                    "The SSE stream ended after dropping %d event(s) over %s per BufferFullPolicy.DROP."
            );
        }

        private void flushDropEpisode(String messageFormat) {
            long count = droppedCount.getAndSet(0);

            if (0 == count) {
                return;
            }

            Instant start = dropEpisodeStart.getAndSet(null);
            Duration episodeDuration = null != start ? Duration.between(start, Instant.now()) : Duration.ZERO;

            LOGGER.log(System.Logger.Level.WARNING, String.format(messageFormat, count, episodeDuration));
        }

        private void notifyDropped(RawSseEvent raw) {
            if (null == droppedHandler) {
                return;
            }

            try {
                droppedHandler.accept(rawMessageFactory.apply(raw));
            } catch (Exception ex) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "The SSE onDropped handler threw an unexpected exception.",
                        ex
                );
            }
        }
    }
}







