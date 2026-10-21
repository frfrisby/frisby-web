package software.frisby.web.server.sse;

import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import software.frisby.core.util.ControlCharacters;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class DefaultSseEmitter implements SseEmitter {
    private static final System.Logger LOGGER = System.getLogger(DefaultSseEmitter.class.getName());

    private static final String EVENT_ARGUMENT_NAME = "event";
    private static final String SINK_ARGUMENT_NAME = "sink";
    private static final String SSE_ARGUMENT_NAME = "sse";
    private static final String SCHEDULED_EVENTS_ARGUMENT_NAME = "scheduledEvents";
    private static final String HEARTBEAT_TEXT = "keep-alive";
    private static final String ABSENT_VALUE = "<none>";

    private final SseEventSink sink;
    private final Sse sse;
    private final AtomicBoolean closed;
    private final ScheduledExecutorService scheduledExecutor;
    private final List<ScheduledFuture<?>> scheduledFutures;

    DefaultSseEmitter(SseEventSink sink, Sse sse, Duration heartbeatInterval, List<ScheduledEvent> scheduledEvents) {
        this.sink = Values.notNull(SINK_ARGUMENT_NAME, sink);
        this.sse = Values.notNull(SSE_ARGUMENT_NAME, sse);
        this.closed = new AtomicBoolean(false);

        Values.notNull(SCHEDULED_EVENTS_ARGUMENT_NAME, scheduledEvents);

        List<ScheduledFuture<?>> futures = new ArrayList<>();
        ScheduledExecutorService executor;

        if (null != heartbeatInterval || !scheduledEvents.isEmpty()) {
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "sse-emitter-scheduler");
                thread.setDaemon(true);
                return thread;
            });

            if (null != heartbeatInterval) {
                // initialDelay is deliberately 0, not heartbeatInterval: a client whose
                // readTimeout equals heartbeatInterval races the first heartbeat's arrival
                // against its own timeout on every connect/reconnect if the first heartbeat
                // (or any other data) doesn't arrive until a full interval has elapsed.
                // Sending immediately on open removes that race entirely; the period still
                // settles into heartbeatInterval for every subsequent heartbeat.
                futures.add(executor.scheduleAtFixedRate(
                        this::sendHeartbeatSafely,
                        0,
                        heartbeatInterval.toMillis(),
                        TimeUnit.MILLISECONDS
                ));
            }

            // Unlike the heartbeat's initialDelay == 0 above, a scheduled event's first tick
            // fires after a full interval has elapsed -- its purpose is "wake up every N
            // minutes," not "stand in for the connection-establishment guard," which is already
            // unconditionally satisfied by the leading comment frame below regardless of whether
            // any scheduled event is configured.
            for (ScheduledEvent scheduledEvent : scheduledEvents) {
                long intervalMillis = scheduledEvent.interval().toMillis();

                futures.add(executor.scheduleAtFixedRate(
                        () -> sendScheduledEventSafely(scheduledEvent),
                        intervalMillis,
                        intervalMillis,
                        TimeUnit.MILLISECONDS
                ));
            }
        } else {
            executor = null;
        }

        if (null == heartbeatInterval) {
            // No recurring heartbeat is configured, but a client's readTimeout still only
            // bounds time-to-first-byte, not the ongoing stream — if the resource method
            // doesn't write its own first event immediately (e.g. it's waiting on a slow
            // upstream call), a client with a comparatively short readTimeout would see the
            // stream fail before ever getting a byte, indistinguishable from a genuinely
            // dead connection. Writing one leading comment frame immediately closes that
            // gap unconditionally, independent of whether heartbeat(...) was ever called.
            // This is a single, one-time frame, not a keep-alive loop — sustained silence
            // later in the stream still requires an explicit heartbeat(...) call.
            sendHeartbeatSafely();
        }

        this.scheduledExecutor = executor;
        this.scheduledFutures = List.copyOf(futures);

        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "SSE stream opened."
            );
        }
    }

    private static Throwable unwrapCompletionException(Throwable cause) {
        if (cause instanceof CompletionException completionException
                && null != completionException.getCause()) {
            return completionException.getCause();
        }

        return cause;
    }

    @Override
    public CompletableFuture<Void> send(SseEvent event) {
        Values.notNull(EVENT_ARGUMENT_NAME, event);

        try {
            OutboundSseEvent outboundEvent = toOutboundEvent(event);
            CompletableFuture<Void> result = new CompletableFuture<>();

            sink.send(outboundEvent).whenComplete((ignored, throwable) -> {
                if (null == throwable) {
                    logSentEvent(event);
                    result.complete(null);
                    return;
                }

                result.completeExceptionally(unwrapCompletionException(throwable));
            });

            return result;
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Override
    public boolean isOpen() {
        return !sink.isClosed();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        for (ScheduledFuture<?> future : scheduledFutures) {
            future.cancel(false);
        }

        if (null != scheduledExecutor) {
            scheduledExecutor.shutdownNow();
        }

        sink.close();

        if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "SSE stream closed."
            );
        }
    }

    private OutboundSseEvent toOutboundEvent(SseEvent event) {
        OutboundSseEvent.Builder builder = sse.newEventBuilder();

        event.id().ifPresent(builder::id);
        event.event().ifPresent(builder::name);
        event.retry().ifPresent(duration -> builder.reconnectDelay(duration.toMillis()));

        builder.data(
                String.class,
                event.data()
        );

        return builder.build();
    }

    private void logSentEvent(SseEvent event) {
        if (!LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            return;
        }

        String id = event.id().orElse(ABSENT_VALUE);
        String name = event.event().orElse(ABSENT_VALUE);
        String retry = event.retry().map(Duration::toMillis).map(String::valueOf).orElse(ABSENT_VALUE);

        LOGGER.log(
                System.Logger.Level.TRACE,
                "SSE event sent. id=''{0}'', event=''{1}'', retryMs=''{2}''.",
                ControlCharacters.escape(id),
                ControlCharacters.escape(name),
                retry
        );
    }

    private void sendHeartbeatSafely() {
        if (sink.isClosed()) {
            return;
        }

        runSafely(
                () -> sink.send(
                        sse.newEventBuilder()
                                .comment(HEARTBEAT_TEXT)
                                .build()
                ),
                "Heartbeat send failed; sink is presumed closed."
        );
    }

    private void sendScheduledEventSafely(ScheduledEvent scheduledEvent) {
        if (sink.isClosed()) {
            return;
        }

        runSafely(
                () -> {
                    // supplier.get() is deliberately inside this try/catch, alongside the
                    // resulting send() -- scheduleAtFixedRate's own contract permanently
                    // suppresses all future ticks the moment any execution throws, so a
                    // transient supplier failure (or send failure) must not escape here.
                    SseEvent event = scheduledEvent.supplier().get();

                    if (null != event) {
                        sink.send(toOutboundEvent(event));
                    }
                },
                "Scheduled event send failed; sink is presumed closed."
        );
    }

    private void runSafely(Runnable action, String failureMessage) {
        try {
            action.run();
        } catch (Exception ex) {
            // INFO, not DEBUG: this is the first (and, because of the isClosed() short-circuit
            // above, the only) point where the server observes that a write to this sink failed.
            // That's the same operational significance as "SSE stream opened"/"SSE stream closed"
            // below, which are already INFO — a client silently going away is a routine, expected
            // event for any SSE server, so this intentionally stops short of WARN to avoid alarm
            // fatigue, but it must not be invisible at a log level production typically runs at.
            if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        failureMessage,
                        ex
                );
            }
        }
    }
}


