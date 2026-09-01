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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Package-private implementation of {@link SseListener}.
 * <p>
 * Owns the reader thread, one dispatch pipeline per registered {@code onEvent(String,
 * SseHandler)}/{@code onEvent(String, SseBatchHandler)} handler (built once in
 * {@link #connectAsync()}, torn down only in {@link #close()}), and the listener's
 * owned executor, if one was created because the caller did not supply their own via
 * {@link SseListenerBuilder#executor}.
 * <p>
 * Every handler's pipeline starts the same way — {@code Buffer<RawSseEvent> →
 * Transform<RawSseEvent, Delivery>} (deserializing, or passing the raw {@code data}
 * string through unchanged for a raw handler; {@link Delivery} pairs the resulting
 * {@link SseMessage} with the {@link RawSseEvent} it came from, so a handler callback
 * exception can still report the untouched wire-format {@code data} string as
 * {@link SseErrorEvent} context) — a failed deserialization is caught inside the
 * {@code Transform} step, logged at {@code WARNING}, routed to {@code onError} with that
 * same raw context, and dropped ({@code null} result) rather than propagated. An
 * {@code onEvent(String, SseHandler)} handler's pipeline ends there with a terminal
 * {@code Action}; an {@code onEvent(String, SseBatchHandler)} handler's pipeline uses
 * {@code Batch<RawSseEvent>} as its own head stage instead of {@code Buffer<RawSseEvent>}
 * — {@code Batch} already has its own internal queue/{@code capacity} and dedicated
 * worker thread (see {@code concurrency.md}), so chaining a separate {@code Buffer} in
 * front of it would cost a second thread per batch handler for no benefit — followed by a
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
 * <strong>{@link BufferFullPolicy}.</strong> {@code BLOCK} posts unconditionally via the
 * pipeline's ordinary blocking {@code post(T)}, relying on {@code Buffer}/{@code Batch}'s
 * natural blocking behavior. {@code DROP}/{@code DISCONNECT} instead call the pipeline's
 * bounded-wait {@code post(T, Duration.ZERO)} (see {@code concurrency.md}'s "Bounded-Wait
 * Posting" section) — an atomic, non-blocking accept-or-reject against the pipeline's own
 * real capacity gate, with no separate pre-check step and therefore no race window, unlike
 * an approach that inspects {@code inFlight()} and posts as two separate calls. {@code DROP}
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

    private final Client client;
    private final List<Consumer<SseSpec>> navigationOps;
    private final String initialLastEventId;
    private final BufferFullPolicy bufferFullPolicy;
    private final Consumer<SseMessage<String>> droppedHandler;
    private final ExecutorService callerExecutor;
    private final Map<String, SseHandler> eventHandlers;
    private final Map<String, SseBatchHandler> batchHandlers;
    private final SseHandler unhandledHandler;
    private final SseBatchHandler unhandledBatchHandler;
    private final Consumer<SseErrorEvent> errorHandler;
    private final RetryDelay reconnectDelay;
    private final Duration closeTimeout;

    private final AtomicBoolean started;
    private final AtomicBoolean closed;
    private final AtomicReference<InputStream> currentStream;

    private ExecutorService pipelineExecutor;
    private NamedExecutorService ownedExecutor;
    private Map<String, Pipeline<RawSseEvent>> handlerPipelines;
    private Pipeline<RawSseEvent> unhandledPipeline;
    private Future<?> readerFuture;

    DefaultSseListener(Client client,
                       List<Consumer<SseSpec>> navigationOps,
                       String initialLastEventId,
                       BufferFullPolicy bufferFullPolicy,
                       Consumer<SseMessage<String>> droppedHandler,
                       ExecutorService callerExecutor,
                       Map<String, SseHandler> eventHandlers,
                       Map<String, SseBatchHandler> batchHandlers,
                       SseHandler unhandledHandler,
                       SseBatchHandler unhandledBatchHandler,
                       Consumer<SseErrorEvent> errorHandler,
                       RetryDelay reconnectDelay,
                       Duration closeTimeout) {
        this.client = client;
        this.navigationOps = navigationOps;
        this.initialLastEventId = initialLastEventId;
        this.bufferFullPolicy = bufferFullPolicy;
        this.droppedHandler = droppedHandler;
        this.callerExecutor = callerExecutor;
        this.eventHandlers = eventHandlers;
        this.batchHandlers = batchHandlers;
        this.unhandledHandler = unhandledHandler;
        this.unhandledBatchHandler = unhandledBatchHandler;
        this.errorHandler = errorHandler;
        this.reconnectDelay = reconnectDelay;
        this.closeTimeout = closeTimeout;
        this.started = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.currentStream = new AtomicReference<>();
        this.pipelineExecutor = null;
        this.ownedExecutor = null;
        this.handlerPipelines = null;
        this.unhandledPipeline = null;
        this.readerFuture = null;
    }

    /**
     * Closes {@code stream}, logging {@code failureMessage} at {@code WARNING} (with the
     * causing {@link IOException} attached) if {@code close()} itself fails, rather than
     * letting that exception propagate. A {@code null} {@code stream} is a no-op — both
     * call sites obtain {@code stream} via {@code currentStream.getAndSet(null)}, which is
     * frequently already {@code null} (no connection currently open).
     * <p>
     * Shared by {@link #close()} (the same failure message every time) and
     * {@link ReaderTask#postWithPolicy} (a distinct message specific to a policy-driven
     * {@link BufferFullPolicy#DISCONNECT}) — extracted here specifically so this
     * catch-and-log behavior is defined, and unit tested, exactly once rather than
     * duplicated at each call site.
     * <p>
     * Package-private rather than {@code private}, matching {@link #throwIfFatal}'s own
     * rationale: this is trivial to unit test directly with an ordinary throwing
     * {@link InputStream} test double, but has no practical way to be exercised through
     * either real call site — neither a normal JDK {@code HttpClient} response stream nor
     * a test server can be coaxed into making {@code close()} itself throw on demand.
     *
     * @param stream         The stream to close; a no-op if {@code null}.
     * @param failureMessage The message to log at {@code WARNING} if {@code close()} throws.
     */
    static void closeStreamSafely(InputStream stream, String failureMessage) {
        if (null == stream) {
            return;
        }

        try {
            stream.close();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, failureMessage, e);
        }
    }

    /**
     * Rethrows {@code t} unchanged if it is fatal to the JVM — {@link VirtualMachineError}
     * (covering {@link OutOfMemoryError}, {@link StackOverflowError}, {@link InternalError},
     * and {@link UnknownError}) or {@link LinkageError} — otherwise returns normally. As
     * opposed to a merely unwelcome throwable (a handler's {@link RuntimeException}, or an
     * {@code AssertionError} escaping a test's callback), which this listener's
     * callback-safety net ({@link #dispatchSafely}/{@link #dispatchBatchSafely}/
     * {@link #notify}/{@link ReaderTask#notifyError}/{@link ReaderTask#notifyDropped}) is
     * designed to swallow and report via {@code onError} rather than let kill a worker
     * thread, a fatal error is deliberately excluded from that net.
     * <p>
     * Package-private rather than {@code private} specifically so it can be unit tested
     * directly — forcing an actual {@link VirtualMachineError}/{@link LinkageError} to
     * occur inside a test's callback is impractical, but this method's own branching is
     * trivial to exercise in isolation with ordinary test doubles of each declared
     * subtype.
     */
    static void throwIfFatal(Throwable t) {
        if (t instanceof VirtualMachineError vme) {
            throw vme;
        }

        if (t instanceof LinkageError le) {
            throw le;
        }
    }

    /**
     * Computes how long a drop episode lasted, for {@link ReaderTask#flushDropEpisode}'s
     * summary log message.
     * <p>
     * {@code start} is {@code null} only if a drop episode was never actually begun —
     * structurally unreachable from {@code flushDropEpisode}'s only call sites, since
     * {@code ReaderTask.recordDrop} always sets {@code dropEpisodeStart} in the same
     * (single, reader) thread and in the same method call that first increments
     * {@code droppedCount} from {@code 0}, and {@code flushDropEpisode} itself already
     * returns early whenever {@code droppedCount} reads as {@code 0}. So by the time this
     * method is ever called with a genuinely {@code null} {@code start}, {@code count}
     * would also have to be {@code 0} — the exact case already filtered out one line
     * earlier. Kept as honest defensive programming (a caller can't structurally prove a
     * {@code null} will never be passed) rather than assuming the invariant always holds;
     * package-private specifically so this branch is directly unit testable, since driving
     * a real reader thread into this state is not practically achievable.
     *
     * @param start The instant the current drop episode began, or {@code null} if none is
     *              in progress.
     * @return The elapsed duration since {@code start}, or {@link Duration#ZERO} if
     * {@code start} is {@code null}.
     */
    static Duration computeDropEpisodeDuration(Instant start) {
        return null != start ? Duration.between(start, Instant.now()) : Duration.ZERO;
    }

    /**
     * Reports whether the reader loop should continue after {@code ReaderTask
     * .awaitReconnectDelay}'s {@code Thread.sleep} returns normally (i.e. was
     * <em>not</em> interrupted) — {@code true} unless {@link #close()} concurrently set
     * {@code closed} in the narrow window between the sleep completing and this check
     * running, without its accompanying {@code readerFuture.cancel(true)} arriving in
     * time to be observed as an {@link InterruptedException} instead. That race window
     * is real but timing-dependent enough that no realistic integration test can force
     * it on demand — extracted to a plain, directly testable static method (both a
     * {@code true} and a {@code false} {@code closed} value are trivial to supply
     * directly) rather than left as an unreachable-in-practice branch inline, per the
     * same reasoning documented on {@link #computeDropEpisodeDuration}.
     *
     * @param closed The listener's shared closed flag.
     * @return {@code true} if the reader loop should continue, {@code false} if
     * {@code closed} was set before this check ran.
     */
    static boolean isStillRunningAfterDelay(AtomicBoolean closed) {
        return !closed.get();
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

        readerFuture = pipelineExecutor.submit(
                new ReaderTask(
                        client,
                        navigationOps,
                        initialLastEventId,
                        handlerPipelines,
                        unhandledPipeline,
                        errorHandler,
                        currentStream,
                        bufferFullPolicy,
                        droppedHandler,
                        this::toRawMessage,
                        reconnectDelay,
                        closed
                )
        );
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
        closeStreamSafely(stream, "Failed to close the SSE input stream.");

        if (null != readerFuture) {
            readerFuture.cancel(true);
        }

        if (null != handlerPipelines) {
            handlerPipelines.values().forEach(Pipeline::complete);
        }

        if (null != unhandledPipeline) {
            unhandledPipeline.complete();
        }

        if (null != handlerPipelines) {
            handlerPipelines.values().forEach(this::awaitCompletionWithTimeout);
        }

        if (null != unhandledPipeline) {
            awaitCompletionWithTimeout(unhandledPipeline);
        }

        if (null != ownedExecutor) {
            ownedExecutor.shutdown();
        }
    }

    /**
     * Bounds {@code pipeline}'s drain wait to {@link #closeTimeout} instead of the
     * unbounded no-arg {@code awaitCompletion()} — required specifically because a
     * pipeline worker thread killed by an external {@code ExecutorService} shutdown
     * (rather than this listener's own graceful {@code complete()}) never resolves its
     * {@code completion()} future, per {@code frisby-core}'s own documented behavior. A
     * caller who supplies their own executor and shuts it down independently, without
     * ever calling {@link #close()} first, would otherwise hang a later {@code close()}
     * call forever. A timeout here is logged at {@code WARNING} rather than thrown,
     * since {@code close()} declares no checked exception.
     */
    private void awaitCompletionWithTimeout(Pipeline<RawSseEvent> pipeline) {
        if (!pipeline.awaitCompletion(closeTimeout)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "A dispatch pipeline did not finish draining within the configured "
                            + "closeTimeout of " + closeTimeout + "; close() is proceeding anyway."
            );
        }
    }


    private Map<String, Pipeline<RawSseEvent>> buildHandlerPipelines() {
        Map<String, Pipeline<RawSseEvent>> pipelines = new HashMap<>();

        for (Map.Entry<String, SseHandler> entry : eventHandlers.entrySet()) {
            pipelines.put(entry.getKey(), buildHandlerPipeline(entry.getValue()));
        }

        // Batch registrations are applied after single-event ones, so an event type
        // registered via both onEvent(String, SseHandler) and onEvent(String,
        // SseBatchHandler) resolves to its batch pipeline — an edge case neither
        // builder rejects, but not an expected usage pattern.
        for (Map.Entry<String, SseBatchHandler> entry : batchHandlers.entrySet()) {
            pipelines.put(entry.getKey(), buildBatchHandlerPipeline(entry.getValue()));
        }

        return pipelines;
    }

    private Pipeline<RawSseEvent> buildHandlerPipeline(SseHandler handler) {
        int capacity = handler.capacity();

        if (1 == handler.concurrency()) {
            return Pipeline.<RawSseEvent>builder()
                    .executor(pipelineExecutor)
                    .from(Buffer.of(RawSseEvent.class)
                            .capacity(capacity))
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
                                        .capacity(capacity))
                                .then(Transform.of(RawSseEvent.class, Delivery.class)
                                        .transform(raw -> deserializeSafely(raw, handler)))
                                .to(delivery -> dispatchSafely(handler, delivery))));
    }

    private Pipeline<RawSseEvent> buildBatchHandlerPipeline(SseBatchHandler handler) {
        int capacity = handler.capacity();
        int batchSize = handler.batchSize();
        Duration batchTimeout = handler.batchTimeout();

        if (1 == handler.concurrency()) {
            return Pipeline.<RawSseEvent>builder()
                    .executor(pipelineExecutor)
                    .from(Batch.of(RawSseEvent.class)
                            .capacity(capacity)
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
                                        .capacity(capacity)
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

    /**
     * Builds the catch-all pipeline for events with no matching {@code onEvent}
     * registration. A registered {@link #unhandledBatchHandler} takes the batch shape
     * ({@link #buildBatchHandlerPipeline}); a registered {@link #unhandledHandler} (or
     * neither being registered) takes the single-event shape
     * ({@link #buildHandlerPipeline(SseHandler)}) — {@link SseListenerBuilder}
     * guarantees at most one of the two is ever set, and both are always raw. Falls back
     * to a no-op raw handler when neither an {@code onUnhandledEvent} handler nor an
     * {@code onUnhandledEvent} batch handler was registered, so {@code #dispatch} always
     * has a pipeline to fall back to.
     */
    private Pipeline<RawSseEvent> buildUnhandledPipeline() {
        if (null != unhandledBatchHandler) {
            return buildBatchHandlerPipeline(unhandledBatchHandler);
        }

        SseHandler handler = null != unhandledHandler
                ? unhandledHandler
                : SseHandler.of(message -> { });

        return buildHandlerPipeline(handler);
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

    private Delivery deserializeSafely(RawSseEvent raw, SseBatchHandler handler) {
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
     * deserialization fails ({@link #deserializeSafely(RawSseEvent, SseBatchHandler)})
     * rather than discarding the whole batch — a single malformed event should never
     * take an entire batch's worth of good events down with it.
     */
    private List<Delivery> deserializeBatchSafely(List<RawSseEvent> raws, SseBatchHandler handler) {
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
        byte[] data = raw.data().getBytes(StandardCharsets.UTF_8);
        Object body;

        if (handler.type().isPresent()) {
            body = client.configuration().serializer().deserialize(data, handler.type().get());
        } else if (handler.genericType().isPresent()) {
            body = client.configuration().serializer().deserialize(data, handler.genericType().get());
        } else {
            body = raw.data();
        }

        return new DefaultSseMessage<>(raw.id(), raw.event(), body, Instant.now());
    }

    private SseMessage<?> toMessage(RawSseEvent raw, SseBatchHandler handler) {
        byte[] data = raw.data().getBytes(StandardCharsets.UTF_8);
        Object body;

        if (handler.type().isPresent()) {
            body = client.configuration().serializer().deserialize(data, handler.type().get());
        } else if (handler.genericType().isPresent()) {
            body = client.configuration().serializer().deserialize(data, handler.genericType().get());
        } else {
            body = raw.data();
        }

        return new DefaultSseMessage<>(raw.id(), raw.event(), body, Instant.now());
    }

    private SseMessage<String> toRawMessage(RawSseEvent raw) {
        return new DefaultSseMessage<>(raw.id(), raw.event(), raw.data(), Instant.now());
    }

    /**
     * Invokes {@code handler}'s callback, isolating the pipeline's dedicated worker
     * thread from any non-fatal failure the callback raises — including an
     * {@link Error} subtype such as {@code AssertionError}/{@code AssertionFailedError}
     * escaping a test's handler callback, not just {@link RuntimeException}. Each
     * registered handler owns exactly one worker per
     * {@link SseHandler#concurrency(int) concurrency} arm; letting a non-fatal
     * {@code Error} propagate out of this method would kill that worker permanently
     * (nothing replaces it), which would in turn hang {@link #close()} forever inside
     * {@code pipeline().awaitCompletion()} waiting for a worker that no longer exists.
     * A callback bug must never be able to wedge the whole connection.
     * <p>
     * A truly fatal JVM-level error ({@link VirtualMachineError} — covering
     * {@link OutOfMemoryError}, {@link StackOverflowError}, and the like — or
     * {@link LinkageError}) is deliberately <em>not</em> swallowed here and is instead
     * rethrown ({@link #throwIfFatal}): the JVM may already be in a corrupted or
     * unrecoverable state at that point, and silently continuing to process further
     * events on this worker thread risks worse outcomes (e.g. quietly-wrong behavior)
     * than letting the thread die — the same trade-off made by
     * {@code Exceptions.throwIfFatal} in RxJava/Reactor.
     * <p>
     * The {@code null == delivery} guard is structurally unreachable through the real
     * {@code Buffer → Transform → Action} pipeline — {@code concurrency}'s own
     * {@code TargetManager.postToTarget} never forwards a {@code null} item to a linked
     * target at all (verified directly against its source), so {@link #deserializeSafely}
     * returning {@code null} on a failed deserialization already stops that item from
     * ever reaching this method, let alone with a null {@code delivery} argument. The
     * guard is kept anyway as honest defensive programming against a direct call (this
     * method is package-private specifically so a test can make one), rather than
     * assuming a caller can never violate the assumption.
     */
    void dispatchSafely(SseHandler handler, Delivery delivery) {
        if (null == delivery) {
            return;
        }

        try {
            invokeCallback(handler.callback(), delivery.message());
        } catch (Throwable e) {
            throwIfFatal(e);

            LOGGER.log(System.Logger.Level.WARNING, "SSE handler callback failed.", e);
            notifyError(toRawMessage(delivery.raw()), e);
        }
    }

    /**
     * See {@link #dispatchSafely(SseHandler, Delivery)} — same rationale for catching
     * {@link Throwable} rather than only {@link RuntimeException}, and the same
     * {@link #throwIfFatal} carve-out for JVM-level errors.
     * <p>
     * Unlike {@link #dispatchSafely}'s {@code null == delivery} guard, this method's
     * {@code deliveries.isEmpty()} half is genuinely reachable through the real
     * {@code Batch → Transform → Action} pipeline: {@link #deserializeBatchSafely} filters
     * a failed item out of the batch individually rather than failing the whole batch, so
     * a batch whose <em>every</em> item fails to deserialize produces a non-null, empty
     * list — nothing to deliver, but not an unreachable state. {@code null == deliveries},
     * on the other hand, is unreachable for the same reason documented on
     * {@link #dispatchSafely}: {@code deserializeBatchSafely} always returns a list, never
     * {@code null}, and {@code concurrency}'s {@code TargetManager.postToTarget} would not
     * forward a null item downstream even if it somehow did. Kept for the same
     * defensive-programming reason, and this method is package-private for the same
     * direct-testability reason.
     */
    void dispatchBatchSafely(SseBatchHandler handler, List<Delivery> deliveries) {
        if (null == deliveries || deliveries.isEmpty()) {
            return;
        }

        List<?> messages = deliveries.stream()
                .map(Delivery::message)
                .toList();

        try {
            invokeBatchCallback(handler.callback(), messages);
        } catch (Throwable e) {
            throwIfFatal(e);

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

    /**
     * Invokes the caller's {@code onError} handler, isolating the calling thread from any
     * non-fatal failure it raises — same rationale and {@link #throwIfFatal} carve-out as
     * {@link #dispatchSafely}. This matters just as much here: this method runs on the same
     * pipeline worker thread as {@link #dispatchSafely}/{@link #dispatchBatchSafely} when
     * invoked from their {@code catch} blocks, so a narrower catch here would silently
     * reopen the exact worker-thread-death/{@code close()}-hang problem those methods exist
     * to prevent, just one call frame removed.
     */
    private void notify(SseErrorEvent event) {
        if (null == errorHandler) {
            return;
        }

        try {
            errorHandler.accept(event);
        } catch (Throwable ex) {
            throwIfFatal(ex);

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

        /**
         * Invokes the caller's {@code onError} handler from the reader thread, isolating it
         * from any non-fatal failure — same rationale and {@link DefaultSseListener#throwIfFatal}
         * carve-out as {@link DefaultSseListener#dispatchSafely}. A narrower catch here would
         * let a buggy {@code onError} handler kill the reader thread itself, silently ending
         * the entire reconnect loop with no further reconnect attempts ever being made.
         */
        private static void notifyError(Consumer<SseErrorEvent> errorHandler, Throwable error) {
            if (null == errorHandler) {
                return;
            }

            try {
                errorHandler.accept(SseErrorEvent.of(error));
            } catch (Throwable ex) {
                throwIfFatal(ex);

                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "The SSE onError handler threw an unexpected exception.",
                        ex
                );
            }
        }

        @Override
        public void run() {
            int consecutiveFailures = 0;

            // The loop's own exit condition is intentionally "while (true)" rather than
            // "while (!closed.get())" — every real exit path already goes through one of
            // the two explicit break statements below (the post-attempt closed.get()
            // check, or awaitReconnectDelay returning false). A "while (!closed.get())"
            // condition here would only ever evaluate false inside the narrow,
            // non-deterministic race between this loop's own top-of-loop check and the
            // post-attempt check just a few lines below it — structurally unreachable
            // through any normal (or realistically test-driven) call path, and therefore
            // permanently uncoverable branch coverage if left in. See SseEventParser's
            // analogous cleanup from Chunk 5 for the same reasoning.
            while (true) {
                try (InputStream in = openStream()) {
                    currentStream.set(in);
                    consecutiveFailures = 0;
                    disconnectRequested.set(false);

                    SseEventParser parser = new SseEventParser(in);
                    Optional<RawSseEvent> event;

                    while (!closed.get() && (event = parser.next()).isPresent()) {
                        RawSseEvent raw = event.get();
                        raw.retry().ifPresent(pendingServerRetryDelay::set);

                        if (dispatch(raw)) {
                            raw.id().ifPresent(lastEventId::set);
                        }
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

        private boolean awaitReconnectDelay(int consecutiveFailures) {
            Duration delay = resolveDelay(consecutiveFailures);

            try {
                Thread.sleep(Math.max(delay.toMillis(), 0L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            return isStillRunningAfterDelay(closed);
        }

        private Duration resolveDelay(int consecutiveFailures) {
            Duration serverDelay = pendingServerRetryDelay.getAndSet(null);

            if (null != serverDelay) {
                return serverDelay;
            }

            return reconnectDelay.delayFor(Math.max(consecutiveFailures, 1));
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
         *
         * @return {@code true} if {@code raw} was actually handed off to a pipeline
         * ({@link BufferFullPolicy#BLOCK} in the overwhelmingly common case — see
         * {@link #postWithPolicy} for the one shutdown-driven exception; {@link BufferFullPolicy#DROP} /
         * {@link BufferFullPolicy#DISCONNECT} only when capacity allowed it), {@code
         * false} if it was dropped or triggered a disconnect — the caller must not
         * advance {@link #lastEventId} for an event this method reports as not posted,
         * since an event never handed to a pipeline must still be replayed by the
         * server after a {@code DISCONNECT}-driven reconnect, not skipped.
         */
        private boolean dispatch(RawSseEvent raw) {
            if (raw.event().isEmpty()) {
                return postWithPolicy(unhandledPipeline, raw);
            }

            Pipeline<RawSseEvent> target = handlerPipelines.getOrDefault(raw.event().get(), unhandledPipeline);
            return postWithPolicy(target, raw);
        }

        /**
         * Applies {@link BufferFullPolicy} before posting. {@code BLOCK} posts
         * unconditionally via the pipeline's ordinary blocking {@code post(T)}, relying on
         * {@code Buffer}/{@code Batch}'s natural blocking behavior — it is deliberately
         * checked first and returns immediately, so it pays no cost for the drop-tracking
         * machinery below. {@code DROP}/{@code DISCONNECT} instead call the pipeline's
         * bounded-wait {@code post(T, Duration.ZERO)} (see {@code concurrency.md}'s
         * "Bounded-Wait Posting" section) — an atomic, non-blocking accept-or-reject against
         * the pipeline's own real capacity gate, with no separate capacity pre-check and
         * therefore no race window between checking and posting.
         * <p>
         * {@code BLOCK}'s call to {@code target.post(raw)} can itself return {@code false}
         * — not because of anything to do with {@code BLOCK}'s own semantics, but because
         * {@code close()} now cancels this reader task via {@code Future.cancel(true)},
         * which interrupts a thread blocked acquiring buffer capacity; {@code frisby-core}'s
         * {@code AsyncBuffer.post(T)} catches that interrupt and returns {@code false}
         * (item not enqueued) rather than throwing. Honoring that return value here —
         * rather than assuming {@code BLOCK} always succeeds — is what stops the caller
         * from incorrectly advancing {@code lastEventId} for an event that was actually
         * silently dropped by the shutdown race, which would otherwise permanently skip
         * replaying it on the next reconnect.
         *
         * @return {@code true} if {@code raw} was posted to {@code target}, {@code false}
         * if it was dropped ({@link BufferFullPolicy#DROP}), triggered a disconnect
         * ({@link BufferFullPolicy#DISCONNECT}), or — under {@link BufferFullPolicy#BLOCK}
         * only — the reader was interrupted (via {@code close()}) while blocked posting.
         */
        private boolean postWithPolicy(Pipeline<RawSseEvent> target, RawSseEvent raw) {
            if (BufferFullPolicy.BLOCK == bufferFullPolicy) {
                return target.post(raw);
            }

            if (BufferFullPolicy.DROP == bufferFullPolicy) {
                if (target.post(raw, Duration.ZERO)) {
                    recordRecoveryIfNeeded();
                    return true;
                }

                recordDrop(raw);
                return false;
            }

            if (target.post(raw, Duration.ZERO)) {
                return true;
            }

            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "The SSE dispatch buffer is full; disconnecting and reconnecting per "
                            + "BufferFullPolicy.DISCONNECT."
            );
            disconnectRequested.set(true);

            InputStream stream = currentStream.getAndSet(null);
            closeStreamSafely(stream, "Failed to close the SSE input stream for a policy-driven disconnect.");

            return false;
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
            Duration episodeDuration = computeDropEpisodeDuration(start);

            LOGGER.log(System.Logger.Level.WARNING, String.format(messageFormat, count, episodeDuration));
        }

        /**
         * Invokes the caller's {@code onDropped} handler from the reader thread, isolating it
         * from any non-fatal failure — same rationale and
         * {@link DefaultSseListener#throwIfFatal} carve-out as
         * {@link DefaultSseListener#dispatchSafely}. A narrower catch here would let a buggy
         * {@code onDropped} handler kill the reader thread itself under exactly the
         * sustained-high-volume conditions {@link BufferFullPolicy#DROP} is meant to survive.
         */
        private void notifyDropped(RawSseEvent raw) {
            if (null == droppedHandler) {
                return;
            }

            try {
                droppedHandler.accept(rawMessageFactory.apply(raw));
            } catch (Throwable ex) {
                throwIfFatal(ex);

                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "The SSE onDropped handler threw an unexpected exception.",
                        ex

                );
            }
        }
    }
}







