package software.frisby.web.client.sse;

import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultSseListener#notifyProcessed(RawSseEvent, String, Instant, Duration, boolean)}.
 * <p>
 * The defensive-catch-and-log branch is difficult to reliably provoke through a live
 * connection on demand — it needs a throwing {@link SseListenerObserver} at exactly the
 * right pipeline stage. {@code notifyProcessed} is deliberately package-private (not
 * {@code private}) so it can be exercised directly instead, with an ordinary
 * {@link RawSseEvent} test double and a throwing observer — the same rationale already
 * established for {@link DefaultSseListenerNotifyReceivedTest}.
 */
class DefaultSseListenerNotifyProcessedTest {
    private static Client testClient() {
        return Client.builder()
                .configuration(c -> c
                        .uri(URI.create("http://localhost"))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();
    }

    private static RawSseEvent rawEvent() {
        return new RawSseEvent(
                Optional.of("1"),
                Optional.of("message"),
                "payload",
                Optional.empty(),
                Instant.now()
        );
    }

    @Test
    void notifiesObserverWithExpectedFields() {
        List<SseEventProcessed> processed = new CopyOnWriteArrayList<>();
        RawSseEvent raw = rawEvent();
        Instant callbackEnd = raw.receivedAt().plusMillis(5);
        Duration processingDuration = Duration.ofMillis(3);

        try (DefaultSseListener listener = (DefaultSseListener) SseListener.builder().client(testClient())
                .path("/sse/stream")
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventProcessed(SseEventProcessed event) {
                        processed.add(event);
                    }
                })
                .build()
        ) {
            listener.notifyProcessed(raw, "message", callbackEnd, processingDuration, true);

            assertEquals(1, processed.size());

            SseEventProcessed event = processed.get(0);
            assertEquals(raw.event(), event.event());
            assertEquals(Optional.of("message"), event.registeredAs());
            assertEquals(raw.id(), event.id());
            assertEquals(raw.receivedAt(), event.receivedAt());
            assertEquals(Duration.between(raw.receivedAt(), callbackEnd), event.totalLatency());
            assertEquals(processingDuration, event.processingDuration());
            assertTrue(event.succeeded());
        }
    }

    @Test
    void observerThrows_isCaughtAndLoggedAtWarning_doesNotPropagate() {
        AtomicBoolean observerInvoked = new AtomicBoolean(false);

        try (DefaultSseListener listener = (DefaultSseListener) SseListener.builder().client(testClient())
                .path("/sse/stream")
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventProcessed(SseEventProcessed event) {
                        observerInvoked.set(true);
                        throw new IllegalStateException("Simulated onEventProcessed observer failure.");
                    }
                })
                .build();
             SystemLogVerifier verifier = SystemLogVerifier.builder()
                     .expect(LogExpectation.builder()
                             .logger(DefaultSseListener.class)
                             .level(System.Logger.Level.WARNING)
                             .predicate(e -> e.message()
                                     .contains("The SSE onEventProcessed observer threw an unexpected exception."))
                             .build()
                     )
                     .build()
        ) {
            RawSseEvent raw = rawEvent();

            assertDoesNotThrow(() ->
                    listener.notifyProcessed(raw, "message", raw.receivedAt(), Duration.ZERO, true)
            );
            assertTrue(observerInvoked.get(), "Expected the throwing observer to actually be invoked");

            verifier.assertExpectations(Duration.ofSeconds(10));
        }
    }
}


