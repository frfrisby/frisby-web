package software.frisby.web.server.sse;

import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class DefaultSseEmitter implements SseEmitter {
    private static final System.Logger LOGGER = System.getLogger(DefaultSseEmitter.class.getName());

    private static final String EVENT_ARGUMENT_NAME = "event";
    private static final String SINK_ARGUMENT_NAME = "sink";
    private static final String SSE_ARGUMENT_NAME = "sse";
    private static final String HEARTBEAT_TEXT = "keep-alive";

    private final SseEventSink sink;
    private final Sse sse;
    private final AtomicBoolean closed;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ScheduledFuture<?> heartbeatFuture;

    DefaultSseEmitter(SseEventSink sink, Sse sse, Duration heartbeatInterval) {
        this.sink = Values.notNull(SINK_ARGUMENT_NAME, sink);
        this.sse = Values.notNull(SSE_ARGUMENT_NAME, sse);
        this.closed = new AtomicBoolean(false);

        ScheduledExecutorService executor = null;
        ScheduledFuture<?> future = null;

        if (null != heartbeatInterval) {
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "sse-emitter-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

            future = executor.scheduleAtFixedRate(
                    this::sendHeartbeatSafely,
                    heartbeatInterval.toMillis(),
                    heartbeatInterval.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        }

        this.heartbeatExecutor = executor;
        this.heartbeatFuture = future;
    }

    @Override
    public CompletableFuture<Void> send(SseEvent event) {
        Values.notNull(EVENT_ARGUMENT_NAME, event);

        try {
            OutboundSseEvent outboundEvent = toOutboundEvent(event);
            CompletableFuture<Void> result = new CompletableFuture<>();

            sink.send(outboundEvent).whenComplete((ignored, throwable) -> {
                if (null == throwable) {
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

        if (null != heartbeatFuture) {
            heartbeatFuture.cancel(false);
        }

        if (null != heartbeatExecutor) {
            heartbeatExecutor.shutdownNow();
        }

        sink.close();
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

    private static Throwable unwrapCompletionException(Throwable cause) {
        if (cause instanceof CompletionException completionException
                && null != completionException.getCause()) {
            return completionException.getCause();
        }

        return cause;
    }

    private void sendHeartbeatSafely() {
        if (sink.isClosed()) {
            return;
        }

        try {
            sink.send(
                    sse.newEventBuilder()
                            .comment(HEARTBEAT_TEXT)
                            .build()
            );
        } catch (Exception ex) {
            LOGGER.log(
                    System.Logger.Level.DEBUG,
                    "Heartbeat send failed.",
                    ex
            );
        }
    }
}




