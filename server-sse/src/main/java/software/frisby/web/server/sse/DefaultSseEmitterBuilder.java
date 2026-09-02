package software.frisby.web.server.sse;

import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Values;

import java.time.Duration;

final class DefaultSseEmitterBuilder implements SseEmitterBuilder {
    private static final String SINK_ARGUMENT_NAME = "sink";
    private static final String SSE_ARGUMENT_NAME = "sse";
    private static final String HEARTBEAT_ARGUMENT_NAME = "heartbeatInterval";

    private SseEventSink sink;
    private Sse sse;
    private Duration heartbeatInterval;

    DefaultSseEmitterBuilder() {
        this.sink = null;
        this.sse = null;
        this.heartbeatInterval = null;
    }

    @Override
    public SseEmitterBuilder sink(SseEventSink sink) {
        this.sink = Values.notNull(
                SINK_ARGUMENT_NAME,
                sink
        );

        return this;
    }

    @Override
    public SseEmitterBuilder sse(Sse sse) {
        this.sse = Values.notNull(
                SSE_ARGUMENT_NAME,
                sse
        );

        return this;
    }

    @Override
    public SseEmitterBuilder heartbeat(Duration heartbeatInterval) {
        this.heartbeatInterval = Durations.positive(
                HEARTBEAT_ARGUMENT_NAME,
                heartbeatInterval
        );

        return this;
    }

    @Override
    public SseEmitter build() {
        Values.notNull(
                SINK_ARGUMENT_NAME,
                sink
        );

        Values.notNull(
                SSE_ARGUMENT_NAME,
                sse
        );

        return new DefaultSseEmitter(
                sink,
                sse,
                heartbeatInterval
        );
    }
}
