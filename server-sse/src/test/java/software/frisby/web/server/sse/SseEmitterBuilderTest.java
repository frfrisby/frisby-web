package software.frisby.web.server.sse;

import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.NullValueException;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class SseEmitterBuilderTest {
    private static final String NULL_SINK_MESSAGE = "The 'sink' value is invalid. The value must not be null.";
    private static final String NULL_SSE_MESSAGE = "The 'sse' value is invalid. The value must not be null.";
    private static final String NULL_SCHEDULED_EVENT_MESSAGE =
            "The 'event' value is invalid. The value must not be null.";

    @Test
    void nullSink_throwsNullValueException() {
        NullValueException ex = assertThrows(
                NullValueException.class,
                () -> SseEmitter.builder().sink(null)
        );

        assertEquals(NULL_SINK_MESSAGE, ex.getMessage());
    }

    @Test
    void nullSse_throwsNullValueException() {
        NullValueException ex = assertThrows(
                NullValueException.class,
                () -> SseEmitter.builder().sse(null)
        );

        assertEquals(NULL_SSE_MESSAGE, ex.getMessage());
    }

    @Test
    void nullHeartbeat_throwsNullValueException() {
        assertThrows(
                NullValueException.class,
                () -> SseEmitter.builder().heartbeat(null)
        );
    }

    @Test
    void zeroHeartbeat_throwsDurationOutsideRangeException() {
        assertThrows(
                DurationOutsideRangeException.class,
                () -> SseEmitter.builder().heartbeat(Duration.ZERO)
        );
    }

    @Test
    void negativeHeartbeat_throwsDurationOutsideRangeException() {
        assertThrows(
                DurationOutsideRangeException.class,
                () -> SseEmitter.builder().heartbeat(Duration.ofMillis(-1))
        );
    }

    @Test
    void nullScheduledEvent_throwsNullValueException() {
        NullValueException ex = assertThrows(
                NullValueException.class,
                () -> SseEmitter.builder().scheduledEvent(null)
        );

        assertEquals(NULL_SCHEDULED_EVENT_MESSAGE, ex.getMessage());
    }

    @Test
    void scheduledEventCalledTwice_bothAreAccepted() {
        SseEmitterBuilder builder = SseEmitter.builder()
                .sink(new NoOpSink())
                .sse(new NoOpSse());

        ScheduledEvent first = ScheduledEvent.builder()
                .interval(Duration.ofMinutes(15))
                .supplier(() -> SseEvent.builder().data("first").build())
                .build();

        ScheduledEvent second = ScheduledEvent.builder()
                .interval(Duration.ofMinutes(30))
                .supplier(() -> SseEvent.builder().data("second").build())
                .build();

        assertSame(builder, builder.scheduledEvent(first));
        assertSame(builder, builder.scheduledEvent(second));

        try (SseEmitter emitter = builder.build()) {
            assertNotNull(emitter);
        }
    }

    @Test
    void validCalls_returnSameBuilder() {
        SseEmitterBuilder builder = SseEmitter.builder();

        assertSame(builder, builder.sink(new NoOpSink()));
        assertSame(builder, builder.sse(new NoOpSse()));
        assertSame(builder, builder.heartbeat(Duration.ofMillis(5)));
        assertSame(builder, builder.scheduledEvent(
                ScheduledEvent.builder()
                        .interval(Duration.ofMinutes(15))
                        .supplier(() -> SseEvent.builder().data("payload").build())
                        .build()
        ));

        try (SseEmitter ignored = builder.build()) {
            assertNotNull(ignored);
        }
    }

    @Test
    void missingSink_throwsNullValueException() {
        NullValueException ex = assertThrows(
                NullValueException.class,
                () -> SseEmitter.builder()
                        .sse(new NoOpSse())
                        .build()
        );

        assertEquals(NULL_SINK_MESSAGE, ex.getMessage());
    }

    @Test
    void missingSse_throwsNullValueException() {
        NullValueException ex = assertThrows(
                NullValueException.class,
                () -> SseEmitter.builder()
                        .sink(new NoOpSink())
                        .build()
        );

        assertEquals(NULL_SSE_MESSAGE, ex.getMessage());
    }

    @Test
    void requiredFieldsPresent_buildsEmitter() {
        try (SseEmitter emitter = SseEmitter.builder()
                .sink(new NoOpSink())
                .sse(new NoOpSse())
                .build()) {
            assertNotNull(emitter);
        }
    }

    private static final class NoOpSink implements SseEventSink {
        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public CompletionStage<?> send(OutboundSseEvent event) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }

    private static final class NoOpSse implements Sse {
        @Override
        public OutboundSseEvent.Builder newEventBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.ws.rs.sse.SseBroadcaster newBroadcaster() {
            throw new UnsupportedOperationException();
        }
    }
}


