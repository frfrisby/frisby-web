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
    void initialLastEventIdSetViaBuilder_skipsAlreadySeenEventsOnTheVeryFirstConnect()
            throws InterruptedException {
        String channel = "initial-last-event-id-set-via-builder";
        List<String> firstPassReceived = new CopyOnWriteArrayList<>();
        CountDownLatch firstPassLatch = new CountDownLatch(3);

        SseListener firstListener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", channel)
                .parameter("count", "3")
                .onEvent("message", SseHandler.of(message -> {
                    firstPassReceived.add(message.body());
                    firstPassLatch.countDown();
                }))
                .build();

        try {
            firstListener.connectAsync();

            assertTrue(firstPassLatch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("event-1", "event-2", "event-3"), firstPassReceived);
        } finally {
            firstListener.close();
        }

        // A brand new listener, seeded with lastEventId("2") — simulating resumption after
        // a process restart with no in-memory record of what was already processed. This
        // is the very first connection attempt for this listener, not a reconnect, so it
        // exercises DefaultSseListener's initialLastEventId seeding specifically, distinct
        // from the connection-tracked Last-Event-ID exercised by every other reconnect test
        // in this class.
        List<String> secondPassReceived = new CopyOnWriteArrayList<>();
        CountDownLatch secondPassLatch = new CountDownLatch(1);

        SseListener secondListener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", channel)
                .parameter("count", "3")
                .lastEventId("2")
                .onEvent("message", SseHandler.of(message -> {
                    secondPassReceived.add(message.body());
                    secondPassLatch.countDown();
                }))
                .build();

        try {
            secondListener.connectAsync();

            assertTrue(secondPassLatch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("event-3"), secondPassReceived);
        } finally {
            secondListener.close();
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
                    // A single append to a thread-safe list is the only shared state this
                    // callback touches, so there is no window where a concurrently-reading
                    // thread can observe a "count" and a "body list" that briefly disagree —
                    // unlike maintaining a separate AtomicInteger counter alongside the list,
                    // which previously let the main thread's assertEquals race the reader
                    // thread's still-in-progress drops (increment visible, add() not yet).
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

            assertTrue(!droppedBodies.isEmpty(), "Expected onDropped to fire at least once");
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
    void onDroppedHandlerThrows_doesNotStopTheReaderThreadFromRecordingSubsequentDrops()
            throws InterruptedException {
        int totalEvents = 20;
        AtomicInteger droppedInvocations = new AtomicInteger(0);
        CountDownLatch firstEventDelivered = new CountDownLatch(1);
        // Waiting for a SECOND invocation, not just the first, is what actually proves the
        // reader thread survived its own onDropped handler throwing — matching the same
        // reasoning already used for onErrorHandlerThrows_duringConnectFailure_... above.
        CountDownLatch secondDropped = new CountDownLatch(2);
        CountDownLatch releaseFirstEvent = new CountDownLatch(1);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message()
                                .contains("The SSE onDropped handler threw an unexpected exception."))
                        .build()
                )
                .build()) {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .parameter("channel", "on-dropped-handler-throws")
                    .parameter("count", String.valueOf(totalEvents))
                    .onBufferFull(BufferFullPolicy.DROP)
                    .onDropped(message -> {
                        droppedInvocations.incrementAndGet();
                        secondDropped.countDown();
                        throw new IllegalStateException("Simulated onDropped handler failure.");
                    })
                    // Same blocking-first-delivery technique as
                    // bufferFullPolicyDrop_discardsOverflow_withoutStoppingTheReader above —
                    // see that test's comment for the full rationale.
                    .onEvent("message", SseHandler.of(message -> {
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

                assertTrue(
                        firstEventDelivered.await(10, TimeUnit.SECONDS),
                        "Expected the first event to be delivered"
                );
                assertTrue(
                        secondDropped.await(10, TimeUnit.SECONDS),
                        "Expected the reader thread to survive the onDropped handler throwing and keep recording drops"
                );
                verifier.assertExpectations(Duration.ofSeconds(10));
                assertTrue(droppedInvocations.get() >= 2);
            } finally {
                releaseFirstEvent.countDown();
                listener.close();
            }
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

    @Test
    void onErrorHandlerThrows_duringConnectFailure_doesNotStopTheReconnectLoop() throws InterruptedException {
        AtomicInteger errorCount = new AtomicInteger(0);
        // Waiting for a SECOND invocation, not just the first, is what actually proves
        // the reader thread survived its own onError handler throwing — a single
        // invocation alone wouldn't distinguish "the reader thread survived" from "it
        // happened to die immediately after the one invocation we waited for."
        CountDownLatch secondError = new CountDownLatch(2);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message()
                                .contains("The SSE onError handler threw an unexpected exception."))
                        .build()
                )
                .build()) {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/this-path-does-not-exist-either")
                    .reconnectDelay(RetryDelay.fixed(Duration.ofMillis(50)))
                    .onEvent("message", SseHandler.of(message -> { }))
                    .onError(error -> {
                        errorCount.incrementAndGet();
                        secondError.countDown();
                        throw new IllegalStateException("Simulated onError handler failure.");
                    })
                    .build();

            try {
                listener.connectAsync();

                assertTrue(secondError.await(10, TimeUnit.SECONDS));
                verifier.assertExpectations(Duration.ofSeconds(10));
                assertTrue(errorCount.get() >= 2, "Expected the reconnect loop to survive the onError handler throwing");
            } finally {
                listener.close();
            }
        }
    }

    @Test
    void connectFailureWithNoOnErrorHandlerRegistered_doesNotStopTheReconnectLoop() throws InterruptedException {
        // No .onError(...) call at all — exercises ReaderTask.notifyError's own
        // null == errorHandler guard, distinct from DefaultSseListener.notify()'s
        // analogous guard (that one fires from a dispatch-pipeline worker thread for
        // deserialization/callback failures; this one fires from the reader thread
        // itself for connect/reconnect failures). Every other connect-failure test in
        // this suite registers onError, so this guard had no coverage at all.
        //
        // Waiting for a SECOND connection attempt, not just the first, is what proves
        // the reader thread survived calling notifyError(null, e) without crashing —
        // same reasoning as the sibling "onError handler throws" test above.
        AtomicInteger securityInvocations = new AtomicInteger(0);
        CountDownLatch secondAttempt = new CountDownLatch(2);

        SecurityProvider countingProvider = request -> {
            securityInvocations.incrementAndGet();
            secondAttempt.countDown();
        };

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/this-path-does-not-exist-at-all")
                .security(countingProvider)
                .reconnectDelay(RetryDelay.fixed(Duration.ofMillis(50)))
                .onEvent("message", SseHandler.of(message -> { }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(
                    secondAttempt.await(10, TimeUnit.SECONDS),
                    "Expected the reconnect loop to survive a connect failure with no onError handler registered"
            );
            assertTrue(securityInvocations.get() >= 2);
        } finally {
            listener.close();
        }
    }

    @Test
    void bufferFullPolicyDisconnect_triggersMultipleReconnects_andDeliversAllEventsExactlyOnce()
            throws InterruptedException {
        int totalEvents = 20;
        AtomicInteger securityInvocations = new AtomicInteger(0);
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(totalEvents);

        SecurityProvider countingProvider = request -> securityInvocations.incrementAndGet();

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "buffer-full-policy-disconnect-explicit")
                .parameter("count", String.valueOf(totalEvents))
                .security(countingProvider)
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
            // BufferFullPolicy.DISCONNECT to trigger repeatedly — this test exists
            // specifically to assert that repeated-disconnect behavior explicitly (via the
            // security provider's invocation count, one per connection attempt), rather
            // than only incidentally as a side effect of the Last-Event-ID replay test.
            assertTrue(latch.await(60, TimeUnit.SECONDS));
            assertEquals(totalEvents, received.size());
            assertEquals(totalEvents, new HashSet<>(received).size(), "Expected no duplicate deliveries");
            assertTrue(
                    securityInvocations.get() > 1,
                    "Expected BufferFullPolicy.DISCONNECT to force more than one connection attempt, saw "
                            + securityInvocations.get()
            );
        } finally {
            listener.close();
        }
    }

    @Test
    void bufferFullPolicyBlock_staysOnOneConnection_deliversAllEventsInOrder() throws InterruptedException {
        int totalEvents = 20;
        AtomicInteger securityInvocations = new AtomicInteger(0);
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(totalEvents);

        SecurityProvider countingProvider = request -> securityInvocations.incrementAndGet();

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "buffer-full-policy-block-explicit")
                .parameter("count", String.valueOf(totalEvents))
                .security(countingProvider)
                // BLOCK is the default; set explicitly here so this test documents its own
                // intent rather than relying on an implicit default that could silently
                // change out from under it.
                .onBufferFull(BufferFullPolicy.BLOCK)
                .onEvent("message", SseHandler.of(message -> {
                    received.add(message.body());
                    sleepBriefly();
                    latch.countDown();
                }).capacity(1))
                .build();

        try {
            listener.connectAsync();

            // capacity(1) with a slow handler and a burst of 20 events forces the reader
            // thread to stall repeatedly on Buffer's own blocking post() — the defining
            // behavior of BLOCK. Proven here by two things a dropping/disconnecting policy
            // could not produce: every event survives (none dropped) in the exact order
            // generated (single in-order pipeline, never fanned out), and the connection
            // never reconnects (securityInvocations stays at 1) — BLOCK never closes the
            // stream, unlike DISCONNECT.
            assertTrue(latch.await(20, TimeUnit.SECONDS));

            List<String> expected = new ArrayList<>();
            for (int i = 1; i <= totalEvents; i++) {
                expected.add("event-" + i);
            }

            assertEquals(expected, received);
            assertEquals(1, securityInvocations.get(), "Expected BLOCK to never force a reconnect");
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




