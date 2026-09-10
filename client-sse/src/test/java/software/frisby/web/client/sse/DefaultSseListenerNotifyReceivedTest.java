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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultSseListener#notifyReceived}.
 * <p>
 * Both branches covered here are difficult to reliably provoke through a live connection
 * on demand: the {@code accepted == false} no-op requires a precisely timed
 * {@link BufferFullPolicy} rejection or reader-shutdown race, and the defensive
 * catch-and-log branch requires a throwing {@link SseListenerObserver} at exactly the
 * right pipeline stage. {@code notifyReceived} is deliberately package-private (not
 * {@code private}) so both can be exercised directly instead, with an ordinary
 * {@link RawSseEvent} test double and a throwing observer — the same rationale already
 * established for {@link DefaultSseListenerDispatchSafelyTest}.
 */
class DefaultSseListenerNotifyReceivedTest {
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
    void notAccepted_isANoOp_observerNeverInvoked() {
        List<SseEventReceived> received = new CopyOnWriteArrayList<>();

        try (DefaultSseListener listener = (DefaultSseListener) SseListener.builder().client(testClient())
                .path("/sse/stream")
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventReceived(SseEventReceived event) {
                        received.add(event);
                    }
                })
                .build()
        ) {
            assertDoesNotThrow(() -> listener.notifyReceived(rawEvent(), "message", false));
            assertTrue(received.isEmpty(), "Expected a rejected post to never notify onEventReceived");
        }
    }

    @Test
    void accepted_notifiesObserverWithExpectedFields() {
        List<SseEventReceived> received = new CopyOnWriteArrayList<>();
        RawSseEvent raw = rawEvent();

        try (DefaultSseListener listener = (DefaultSseListener) SseListener.builder().client(testClient())
                .path("/sse/stream")
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventReceived(SseEventReceived event) {
                        received.add(event);
                    }
                })
                .build()
        ) {
            listener.notifyReceived(raw, "message", true);

            assertEquals(1, received.size());

            SseEventReceived event = received.get(0);
            assertEquals(raw.event(), event.event());
            assertEquals(Optional.of("message"), event.registeredAs());
            assertEquals(raw.id(), event.id());
            assertEquals(raw.receivedAt(), event.receivedAt());
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
                    public void onEventReceived(SseEventReceived event) {
                        observerInvoked.set(true);
                        throw new IllegalStateException("Simulated onEventReceived observer failure.");
                    }
                })
                .build();
             SystemLogVerifier verifier = SystemLogVerifier.builder()
                     .expect(LogExpectation.builder()
                             .logger(DefaultSseListener.class)
                             .level(System.Logger.Level.WARNING)
                             .predicate(e -> e.message()
                                     .contains("The SSE onEventReceived observer threw an unexpected exception."))
                             .build()
                     )
                     .build()
        ) {
            assertDoesNotThrow(() -> listener.notifyReceived(rawEvent(), "message", true));
            assertTrue(observerInvoked.get(), "Expected the throwing observer to actually be invoked");

            verifier.assertExpectations(Duration.ofSeconds(10));
        }
    }
}

