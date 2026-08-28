package software.frisby.web.client.sse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.client.RetryDelay;
import software.frisby.web.client.security.SecurityProvider;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunk 9 integration tests — reconnect loop, {@code Last-Event-ID} replay, server
 * {@code retry} handling, and {@link BufferFullPolicy} — against {@code SseTestResource}
 * in {@code test-support}.
 * <p>
 * Every test uses a unique {@code channel} query parameter so the resource's in-memory
 * per-channel event log does not leak state between tests.
 */
class ClientSseReconnectTest {
    private static Server server;
    private static Client client;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(c -> c
                        .port(0)
                        .host("localhost")
                        .serializer(JacksonSerializer.builder().build())
                )
                .resources(TestResources.all())
                .components(TestLogging.forClass(ClientSseReconnectTest.class))
                .build();

        server.start();

        client = Client.builder()
                .configuration(c -> c
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    @Test
    void connectionCloses_reconnectsAndReappliesSecurity() throws InterruptedException {
        CountDownLatch connectLatch = new CountDownLatch(3);
        AtomicInteger securityInvocations = new AtomicInteger(0);

        SecurityProvider countingProvider = request -> {
            securityInvocations.incrementAndGet();
            connectLatch.countDown();
        };

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "reconnect-reapplies-security")
                .parameter("count", "1")
                .security(countingProvider)
                .reconnectDelay(RetryDelay.fixed(Duration.ofMillis(50)))
                .onEvent("message", SseHandler.of(message -> { }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(connectLatch.await(10, TimeUnit.SECONDS));
            assertTrue(securityInvocations.get() >= 3);
        } finally {
            listener.close();
        }
    }

    @Test
    void serverRetryField_honoredForNextAttemptOnly_thenFallsBackToConfiguredStrategy() throws InterruptedException {
        List<Instant> connectTimestamps = new CopyOnWriteArrayList<>();
        CountDownLatch connectLatch = new CountDownLatch(3);

        SecurityProvider timestampingProvider = request -> {
            connectTimestamps.add(Instant.now());
            connectLatch.countDown();
        };

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "server-retry-field-honored")
                .parameter("count", "1")
                .parameter("retryMs", "50")
                .security(timestampingProvider)
                .reconnectDelay(RetryDelay.fixed(Duration.ofSeconds(4)))
                .onEvent("message", SseHandler.of(message -> { }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(connectLatch.await(15, TimeUnit.SECONDS));

            Duration firstReconnectGap = Duration.between(connectTimestamps.get(0), connectTimestamps.get(1));
            Duration secondReconnectGap = Duration.between(connectTimestamps.get(1), connectTimestamps.get(2));

            // The server-supplied retry: 50 applies to the very next attempt only.
            assertTrue(
                    firstReconnectGap.toMillis() < 2_000,
                    "Expected the server retry value to produce a quick reconnect, was " + firstReconnectGap
            );

            // The second reconnect has no server retry value pending, so it falls back to the
            // configured 4 s fixed strategy.
            assertTrue(
                    secondReconnectGap.toMillis() >= 3_000,
                    "Expected the fallback reconnectDelay to apply, was " + secondReconnectGap
            );
        } finally {
            listener.close();
        }
    }

    @Test
    void lastEventId_carriedIntoReconnect_soAllEventsEventuallyDeliveredExactlyOnce() throws InterruptedException {
        int totalEvents = 20;
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(totalEvents);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "last-event-id-replay-on-disconnect")
                .parameter("count", String.valueOf(totalEvents))
                .onBufferFull(BufferFullPolicy.DISCONNECT)
                .reconnectDelay(RetryDelay.fixed(Duration.ofMillis(50)))
                .onEvent("message", SseHandler.of(message -> {
                    received.add(message.body());
                    sleepBriefly();
                    latch.countDown();
                }).capacity(2))
                .build();

        try {
            listener.connectAsync();

            // capacity(2) with a 50 ms-per-event handler and a burst of 20 events forces
            // BufferFullPolicy.DISCONNECT to trigger repeatedly (roughly every couple of
            // events) — that repeated disconnect/Last-Event-ID-replay cycle is exactly what
            // this test exists to exercise, so a generous timeout is required to let every
            // reconnect round-trip complete.
            assertTrue(latch.await(60, TimeUnit.SECONDS));
            assertEquals(totalEvents, received.size());
            assertEquals(totalEvents, new HashSet<>(received).size(), "Expected no duplicate deliveries");

            List<String> expected = new ArrayList<>();
            for (int i = 1; i <= totalEvents; i++) {
                expected.add("event-" + i);
            }
            assertEquals(expected, received);
        } finally {
            listener.close();
        }
    }

    @Test
    void bufferFullPolicyDrop_discardsOverflow_withoutStoppingTheReader() throws InterruptedException {
        int totalEvents = 20;
        AtomicInteger deliveredCount = new AtomicInteger(0);
        AtomicInteger droppedCount = new AtomicInteger(0);
        List<String> droppedBodies = new CopyOnWriteArrayList<>();
        CountDownLatch firstEventDelivered = new CountDownLatch(1);
        CountDownLatch someDropped = new CountDownLatch(1);
        CountDownLatch releaseFirstEvent = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "buffer-full-policy-drop")
                .parameter("count", String.valueOf(totalEvents))
                .onBufferFull(BufferFullPolicy.DROP)
                .onDropped(message -> {
                    droppedCount.incrementAndGet();
                    droppedBodies.add(message.body());
                    someDropped.countDown();
                })
                // Blocking on the very first delivery — rather than merely being slow —
                // deterministically keeps the capacity-1 pipeline's single slot occupied
                // ("in-flight") for as long as the test needs, so every event the reader
                // thread posts in the meantime is guaranteed to be dropped. This replaces
                // a previous version of this test that raced a fixed Thread.sleep against
                // the server's real production speed, which was occasionally flaky.
                //
                // Deliberately does NOT assert here: this runs on the connection's own
                // dedicated dispatch worker thread, not the test thread, and an
                // AssertionFailedError is an Error (not a RuntimeException) that
                // DefaultSseListener's callback-safety net does not swallow, so it would
                // otherwise kill this capacity-1 handler's sole worker permanently and
                // hang listener.close() forever inside awaitCompletion(). The await's
                // timeout is purely a safety net in case the main thread's own release
                // (below, in the finally block) never happens for some other reason —
                // every real assertion about this test's behavior belongs on the main
                // thread instead.
                .onEvent("message", SseHandler.of(message -> {
                    deliveredCount.incrementAndGet();
                    firstEventDelivered.countDown();

                    try {
                        releaseFirstEvent.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).capacity(1))
                .build();

        try {
            listener.connectAsync();

            assertTrue(firstEventDelivered.await(10, TimeUnit.SECONDS), "Expected the first event to be delivered");
            assertTrue(
                    someDropped.await(10, TimeUnit.SECONDS),
                    "Expected at least one event to be dropped while the handler was blocked"
            );

            assertTrue(droppedCount.get() > 0, "Expected onDropped to fire at least once");
            assertEquals(droppedCount.get(), droppedBodies.size());
            assertTrue(
                    deliveredCount.get() < totalEvents,
                    "Expected DROP to discard at least some of the burst, delivered " + deliveredCount.get()
            );
        } finally {
            releaseFirstEvent.countDown();
            listener.close();
        }
    }

    @Test
    void bufferFullPolicyDrop_logsOnlyEpisodeBoundaries_notOnePerDroppedEvent() throws InterruptedException {
        int totalEvents = 20;
        AtomicInteger deliveredCount = new AtomicInteger(0);
        CountDownLatch firstDelivered = new CountDownLatch(1);
        CountDownLatch allDelivered = new CountDownLatch(totalEvents);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("dropping events"))
                        .build()
                )
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("event(s)"))
                        .build()
                )
                .build()) {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .parameter("channel", "buffer-full-policy-drop-log-boundaries")
                    .parameter("count", String.valueOf(totalEvents))
                    .onBufferFull(BufferFullPolicy.DROP)
                    .onEvent("message", SseHandler.of(message -> {
                        // The first delivery races ahead of the burst so the reader thread
                        // fills the capacity-1 buffer and starts dropping; slowing down only
                        // the first delivery lets the remaining backlog (if any survives)
                        // drain quickly once the reader is done producing.
                        if (0 == deliveredCount.getAndIncrement()) {
                            sleepBriefly();
                            firstDelivered.countDown();
                        }

                        allDelivered.countDown();
                    }).capacity(1))
                    .build();

            try {
                listener.connectAsync();

                assertTrue(firstDelivered.await(10, TimeUnit.SECONDS));
                verifier.assertExpectations(Duration.ofSeconds(10));
            } finally {
                listener.close();
            }
        }
    }

    @Test
    void onErrorFiresOnUnrecoverableFailure_andCloseFromWithinHandlerStopsReconnecting()
            throws InterruptedException {
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch firstError = new CountDownLatch(1);
        AtomicReference<SseListener> listenerRef = new AtomicReference<>();

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/this-path-does-not-exist")
                .reconnectDelay(RetryDelay.fixed(Duration.ofMillis(50)))
                .onEvent("message", SseHandler.of(message -> { }))
                .onError(error -> {
                    errorCount.incrementAndGet();
                    firstError.countDown();

                    SseListener current = listenerRef.get();
                    if (null != current) {
                        current.close();
                    }
                })
                .build();

        listenerRef.set(listener);

        try {
            listener.connectAsync();

            assertTrue(firstError.await(10, TimeUnit.SECONDS));

            int countAfterClose = errorCount.get();
            Thread.sleep(500);

            assertEquals(countAfterClose, errorCount.get(), "Expected reconnecting to stop once close() was called");
        } finally {
            listener.close();
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}




