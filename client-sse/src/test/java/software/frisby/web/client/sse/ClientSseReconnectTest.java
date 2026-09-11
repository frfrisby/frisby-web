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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

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
                // Bounded exponential rather than fixed — this test only cares that
                // reconnects keep happening at least 3 times, not the exact cadence, so a
                // strategy that would naturally self-throttle under CI contention (rather
                // than one that provides no such margin) is strictly safer here too.
                .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)))
                .onEvent("message", SseHandler.of(message -> {
                }))
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
                // Deliberately left as fixed(), unlike the other reconnect-loop tests in
                // this class: the whole point of this test is to prove the *configured*
                // reconnectDelay strategy is honored as the fallback once the server's
                // one-shot retry field has been consumed, so the assertions below need a
                // single, known delay value to compare the second reconnect gap against.
                // Safe to leave fixed — every reconnect here follows a clean end-of-stream
                // (count=1, no BufferFullPolicy involved), so there is no failure/disconnect
                // loop that could compound under contention the way the fixed-delay bug in
                // bufferFullPolicyDisconnect_... did.
                .reconnectDelay(RetryDelay.fixed(Duration.ofSeconds(4)))
                .onEvent("message", SseHandler.of(message -> {
                }))
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
        int totalEvents = 6;
        AtomicInteger securityInvocations = new AtomicInteger(0);
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(totalEvents);

        SecurityProvider countingProvider = request -> securityInvocations.incrementAndGet();

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "last-event-id-replay-on-disconnect")
                .parameter("count", String.valueOf(totalEvents))
                .parameter("maxEventsPerConnection", "1")
                .security(countingProvider)
                // Bounded exponential rather than fixed — same rationale as
                // connectionCloses_reconnectsAndReappliesSecurity above: this test asserts
                // eventual full replay, not a specific cadence, across up to six
                // reconnects, so a self-throttling strategy is strictly safer under CI
                // contention with no loss of test intent.
                .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(2)))
                .onEvent("message", SseHandler.of(message -> {
                    received.add(message.body());
                    latch.countDown();
                }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(
                    latch.await(20, TimeUnit.SECONDS),
                    "Timed out waiting for full replay: received=" + received.size()
                            + ", unique=" + new HashSet<>(received).size()
                            + ", securityInvocations=" + securityInvocations.get()
            );
            assertEquals(totalEvents, received.size());
            assertEquals(totalEvents, new HashSet<>(received).size(), "Expected no duplicate deliveries");
            assertTrue(
                    securityInvocations.get() > 1,
                    "Expected replay to require multiple connection attempts, saw " + securityInvocations.get()
            );

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
                .observer(new SseListenerObserver() {
                    @Override
                    public void onDropped(SseMessage<String> message) {
                        // A single append to a thread-safe list is the only shared state this
                        // callback touches, so there is no window where a concurrently-reading
                        // thread can observe a "count" and a "body list" that briefly disagree —
                        // unlike maintaining a separate AtomicInteger counter alongside the list,
                        // which previously let the main thread's assertEquals race the reader
                        // thread's still-in-progress drops (increment visible, add() not yet).
                        droppedBodies.add(message.body());
                        someDropped.countDown();
                    }
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

            assertFalse(droppedBodies.isEmpty(), "Expected onDropped to fire at least once");
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
                                .contains("The SSE onDropped observer threw an unexpected exception."))
                        .build()
                )
                .build()) {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .parameter("channel", "on-dropped-handler-throws")
                    .parameter("count", String.valueOf(totalEvents))
                    .onBufferFull(BufferFullPolicy.DROP)
                    .observer(new SseListenerObserver() {
                        @Override
                        public void onDropped(SseMessage<String> message) {
                            droppedInvocations.incrementAndGet();
                            secondDropped.countDown();
                            throw new IllegalStateException("Simulated onDropped handler failure.");
                        }
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
        CountDownLatch releaseFirstDelivery = new CountDownLatch(1);

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
                        // Block the first delivery to deterministically keep the capacity-1
                        // worker occupied while the reader continues posting, forcing DROP.
                        if (0 == deliveredCount.getAndIncrement()) {
                            firstDelivered.countDown();

                            try {
                                releaseFirstDelivery.await(30, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }).capacity(1))
                    .build();

            try {
                listener.connectAsync();

                assertTrue(firstDelivered.await(10, TimeUnit.SECONDS));
                verifier.assertExpectations(Duration.ofSeconds(10));

                releaseFirstDelivery.countDown();
            } finally {
                releaseFirstDelivery.countDown();
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
                // Bounded exponential rather than fixed — this test only needs one error
                // to fire before it closes the listener, so the exact cadence doesn't
                // matter; kept consistent with the other reconnect-loop tests below.
                .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)))
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onError(SseErrorEvent error) {
                        errorCount.incrementAndGet();
                        firstError.countDown();

                        SseListener current = listenerRef.get();
                        if (null != current) {
                            current.close();
                        }
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
                                .contains("The SSE onError observer threw an unexpected exception."))
                        .build()
                )
                .build()) {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/this-path-does-not-exist-either")
                    // Bounded exponential rather than fixed — this test only needs two
                    // errors to fire; kept consistent with the other reconnect-loop tests.
                    .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)))
                    .onEvent("message", SseHandler.of(message -> {
                    }))
                    .observer(new SseListenerObserver() {
                        @Override
                        public void onError(SseErrorEvent error) {
                            errorCount.incrementAndGet();
                            secondError.countDown();
                            throw new IllegalStateException("Simulated onError handler failure.");
                        }
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
                // Bounded exponential rather than fixed — this test only needs a second
                // connection attempt to occur; kept consistent with the other
                // reconnect-loop tests above.
                .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)))
                .onEvent("message", SseHandler.of(message -> {
                }))
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
        CountDownLatch firstEventDelivered = new CountDownLatch(1);
        CountDownLatch secondConnectionAttempt = new CountDownLatch(2);
        CountDownLatch releaseFirstEvent = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(totalEvents);

        SecurityProvider countingProvider = request -> {
            securityInvocations.incrementAndGet();
            secondConnectionAttempt.countDown();
        };

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "buffer-full-policy-disconnect-explicit")
                .parameter("count", String.valueOf(totalEvents))
                .security(countingProvider)
                .onBufferFull(BufferFullPolicy.DISCONNECT)
                // A bounded exponential strategy — rather than a fixed delay — lets
                // repeated DISCONNECT-driven reconnects self-throttle if the dispatch
                // pipeline's worker thread is slow to get scheduled (e.g. a CPU-contended
                // CI runner): each reconnect now counts toward backoff (see
                // DefaultSseListener.ReaderTask.run()'s consecutiveSetbacks), so the
                // cadence naturally slows down and gives the worker room to drain instead
                // of hammering the server at a constant 50ms forever if the worker falls
                // behind. A fixed delay here previously caused an observed CI failure —
                // 2230 reconnects in 120s, i.e. the reconnect loop never converging.
                .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(2)))
                .onEvent("message", SseHandler.of(message -> {
                    received.add(message.body());

                    if (1 == received.size()) {
                        firstEventDelivered.countDown();

                        try {
                            releaseFirstEvent.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    latch.countDown();
                }).capacity(2))
                .build();

        try {
            listener.connectAsync();

            // Deterministically block the first callback to force the dispatch pipeline to
            // back up and trigger DISCONNECT; then verify at least one reconnect attempt
            // happened before releasing and allowing the full stream to drain.
            assertTrue(firstEventDelivered.await(10, TimeUnit.SECONDS), "Expected the first event to be delivered");
            assertTrue(
                    secondConnectionAttempt.await(10, TimeUnit.SECONDS),
                    "Expected BufferFullPolicy.DISCONNECT to trigger a reconnect while the first callback was blocked"
            );

            releaseFirstEvent.countDown();

            // 60 s. This test's own comment previously assumed a "pathological worst case"
            // of monotonic backoff escalation with no resets — six escalating attempts plus
            // four capped at ~2 s, netting ~11-13 s. That assumption is invalid: verified
            // directly via bufferFullPolicyDisconnect_consecutiveSetbackCounter_
            // escalatesMonotonicallyWithNoReset (below) that the consecutive-setback counter
            // itself escalates correctly with no reset bug, so the actual culprit is the
            // *intentional* design in DefaultSseListener.ReaderTask.run(): the counter
            // resets to zero on any connection attempt that completes with no exception at
            // all, not just a fully-clean end-of-stream. Under a CPU-contended CI runner
            // (e.g. running alongside a Sonar analysis pass), the worker thread can
            // repeatedly manage just enough scheduling to drain a couple of events —
            // resetting the backoff to zero — before stalling and hitting DISCONNECT again,
            // producing many more (but still forward-progressing, non-duplicating) low-delay
            // reconnects than the monotonic-escalation estimate ever accounted for. A CI
            // failure previously observed here (received=19/20, zero duplicates,
            // securityInvocations=514) is consistent with exactly this: real, steady forward
            // progress that simply needed more wall-clock time than budgeted, not a stalled
            // or runaway reconnect loop. 60 s gives a substantially wider margin for that
            // reset-driven worst case while still failing far faster than the original 120 s
            // if a genuine regression ever reintroduces true non-convergence.
            assertTrue(
                    latch.await(60, TimeUnit.SECONDS),
                    "Timed out waiting for full replay: received=" + received.size()
                            + ", unique=" + new HashSet<>(received).size()
                            + ", securityInvocations=" + securityInvocations.get()
            );
            assertEquals(totalEvents, received.size());
            assertEquals(totalEvents, new HashSet<>(received).size(), "Expected no duplicate deliveries");
            assertTrue(
                    securityInvocations.get() > 1,
                    "Expected BufferFullPolicy.DISCONNECT to force more than one connection attempt, saw "
                            + securityInvocations.get()
            );
        } finally {
            releaseFirstEvent.countDown();
            listener.close();
        }
    }

    /**
     * Isolates the reconnect loop's consecutive-setback bookkeeping ({@code
     * DefaultSseListener.ReaderTask.run()}'s {@code consecutiveSetbacks} counter) from the
     * ambient CI-timing noise that made
     * {@link #bufferFullPolicyDisconnect_triggersMultipleReconnects_andDeliversAllEventsExactlyOnce}
     * an unreliable way to catch a regression here — that test only asserts a wall-clock
     * outcome (all events eventually delivered within a generous timeout), so a slow but
     * still-escalating backoff and a backoff that never escalates at all are both
     * indistinguishable failure modes from its perspective, and both can independently blow
     * through even a generous timeout on a sufficiently loaded CI runner.
     * <p>
     * This test instead pins the handler's callback to permanently block after the very
     * first event — capacity(1) is then permanently occupied for the rest of the test, so
     * every single subsequent reconnect attempt's very first replayed event immediately
     * re-triggers {@link BufferFullPolicy#DISCONNECT} again, forever. That gives a
     * deterministic, unbounded stream of consecutive setbacks to inspect directly via
     * {@link SseListenerObserver#onReconnect}, independent of real wall-clock delivery
     * progress or CI scheduling — the assertions below are on the reported
     * {@link SseReconnectEvent#attempt()} sequence and computed {@link SseReconnectEvent#delay()}
     * values themselves, not on whether all events were ever delivered.
     */
    @Test
    void bufferFullPolicyDisconnect_consecutiveSetbackCounter_escalatesMonotonicallyWithNoReset()
            throws InterruptedException {
        int reconnectsToObserve = 8;
        Duration baseDelay = Duration.ofMillis(20);
        Duration maxDelay = Duration.ofMillis(500);
        List<SseReconnectEvent> reconnects = new CopyOnWriteArrayList<>();
        CountDownLatch enoughReconnects = new CountDownLatch(reconnectsToObserve);
        CountDownLatch firstEventDelivered = new CountDownLatch(1);
        CountDownLatch blockForever = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "buffer-full-policy-disconnect-backoff-counter")
                .parameter("count", "5")
                .onBufferFull(BufferFullPolicy.DISCONNECT)
                .reconnectDelay(RetryDelay.exponential(baseDelay, maxDelay))
                .onEvent("message", SseHandler.of(message -> {
                    firstEventDelivered.countDown();

                    try {
                        // Deliberately never counted down until this test's own finally
                        // block — see the class-level javadoc above for why a permanently
                        // blocked callback is exactly what makes the resulting reconnect
                        // sequence deterministic.
                        blockForever.await(60, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).capacity(1))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onReconnect(SseReconnectEvent event) {
                        reconnects.add(event);
                        enoughReconnects.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(firstEventDelivered.await(10, TimeUnit.SECONDS), "Expected the first event to be delivered");
            assertTrue(
                    enoughReconnects.await(30, TimeUnit.SECONDS),
                    "Expected at least " + reconnectsToObserve + " consecutive DISCONNECT-driven "
                            + "reconnects; only saw " + reconnects.size()
            );

            List<Integer> attempts = reconnects.stream()
                    .map(SseReconnectEvent::attempt)
                    .limit(reconnectsToObserve)
                    .toList();

            List<Integer> expectedAttempts = IntStream.rangeClosed(1, reconnectsToObserve).boxed().toList();

            assertEquals(
                    expectedAttempts,
                    attempts,
                    "Expected the consecutive-setback counter to increment by exactly one on "
                            + "every DISCONNECT-driven reconnect with no reset in between, but saw: "
                            + attempts
            );

            assertTrue(
                    reconnects.stream().limit(reconnectsToObserve)
                            .allMatch(event -> SseReconnectCause.BUFFER_FULL == event.cause()),
                    "Expected every reconnect in this scenario to be BUFFER_FULL-caused"
            );
            assertTrue(
                    reconnects.stream().limit(reconnectsToObserve)
                            .allMatch(event -> Optional.of("message").equals(event.eventType())),
                    "Expected every reconnect to report the 'message' handler's pipeline as the trigger"
            );

            // Exponential(baseDelay, maxDelay) with up to 20% jitter, no reset: attempt N's
            // reported delay must be no smaller than the uncapped, unjittered value for
            // attempt N. A delay that small can only mean the escalation silently reset back
            // toward the base delay somewhere in the sequence — the exact symptom this test
            // exists to catch.
            for (int i = 0; i < reconnectsToObserve; i++) {
                int attempt = attempts.get(i);
                int shift = Math.min(attempt - 1, 20);
                long minExpectedMs = Math.min(baseDelay.toMillis() * (1L << shift), maxDelay.toMillis());
                long actualMs = reconnects.get(i).delay().toMillis();

                assertTrue(
                        actualMs >= minExpectedMs,
                        "Reconnect #" + (i + 1) + " (attempt " + attempt + ") computed a delay of "
                                + actualMs + "ms, expected at least " + minExpectedMs + "ms"
                );
            }
        } finally {
            blockForever.countDown();
            listener.close();
        }
    }

    @Test
    void bufferFullPolicyBlock_staysOnOneConnection_deliversAllEventsInOrder() throws InterruptedException {
        int totalEvents = 20;
        AtomicInteger securityInvocations = new AtomicInteger(0);
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch firstEventDelivered = new CountDownLatch(1);
        CountDownLatch releaseFirstEvent = new CountDownLatch(1);
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

                    if (1 == received.size()) {
                        firstEventDelivered.countDown();

                        try {
                            releaseFirstEvent.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    latch.countDown();
                }).capacity(1))
                .build();

        try {
            listener.connectAsync();

            assertTrue(firstEventDelivered.await(10, TimeUnit.SECONDS), "Expected the first event to be delivered");

            releaseFirstEvent.countDown();

            // 30 s — consistent with the sibling bufferFullPolicyDisconnect_... test
            // above, though the reasoning here is simpler: BLOCK never reconnects (see
            // the securityInvocations == 1 assertion below), so there is no
            // escalating-backoff variable at all — just a capacity-1 buffer draining 20
            // near-instant callbacks on a single connection, which measures at well under
            // 1 s locally. 30 s is a still-generous ceiling that fails far faster than the
            // previous 60 s if delivery ever genuinely stalls.
            assertTrue(
                    latch.await(30, TimeUnit.SECONDS),
                    "Timed out waiting for full BLOCK delivery: received=" + received.size()
                            + ", securityInvocations=" + securityInvocations.get()
            );

            List<String> expected = new ArrayList<>();
            for (int i = 1; i <= totalEvents; i++) {
                expected.add("event-" + i);
            }

            assertEquals(expected, received);
            assertEquals(1, securityInvocations.get(), "Expected BLOCK to never force a reconnect");
        } finally {
            releaseFirstEvent.countDown();
            listener.close();
        }
    }

    @Test
    void onReconnectFiresWithFailureCause_forEveryConnectFailure_withEscalatingAttemptAndEmptyEventType()
            throws InterruptedException {
        List<SseReconnectEvent> reconnects = new CopyOnWriteArrayList<>();
        CountDownLatch secondReconnect = new CountDownLatch(2);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/this-path-does-not-exist-for-reconnect-event")
                // Bounded exponential rather than fixed — this test only needs two
                // onReconnect invocations; kept consistent with the other
                // reconnect-loop tests above.
                .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)))
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onReconnect(SseReconnectEvent event) {
                        reconnects.add(event);
                        secondReconnect.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(
                    secondReconnect.await(10, TimeUnit.SECONDS),
                    "Expected onReconnect to fire at least twice for repeated connect failures"
            );

            SseReconnectEvent first = reconnects.get(0);
            SseReconnectEvent second = reconnects.get(1);

            assertEquals(SseReconnectCause.FAILURE, first.cause());
            assertEquals(Optional.empty(), first.eventType());
            assertEquals(1, first.attempt());
            assertFalse(first.delay().isNegative());

            assertEquals(SseReconnectCause.FAILURE, second.cause());
            assertEquals(Optional.empty(), second.eventType());
            assertEquals(2, second.attempt(), "Expected the attempt count to escalate on consecutive failures");
            assertFalse(second.delay().isNegative());
        } finally {
            listener.close();
        }
    }

    @Test
    void onReconnectFiresWithBufferFullCause_andRegisteredEventType_forPolicyDrivenDisconnect()
            throws InterruptedException {
        int totalEvents = 20;
        List<SseReconnectEvent> reconnects = new CopyOnWriteArrayList<>();
        CountDownLatch firstEventDelivered = new CountDownLatch(1);
        CountDownLatch firstReconnect = new CountDownLatch(1);
        CountDownLatch releaseFirstEvent = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "on-reconnect-buffer-full-event-type")
                .parameter("count", String.valueOf(totalEvents))
                .onBufferFull(BufferFullPolicy.DISCONNECT)
                // Bounded exponential — same rationale as
                // bufferFullPolicyDisconnect_triggersMultipleReconnects_... above: lets
                // repeated DISCONNECT-driven reconnects self-throttle instead of
                // hammering the server at a constant rate under CI contention.
                .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(2)))
                .onEvent("message", SseHandler.of(message -> {
                    if (1 == firstEventDelivered.getCount()) {
                        firstEventDelivered.countDown();

                        try {
                            releaseFirstEvent.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }).capacity(2))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onReconnect(SseReconnectEvent event) {
                        reconnects.add(event);
                        firstReconnect.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(firstEventDelivered.await(10, TimeUnit.SECONDS), "Expected the first event to be delivered");
            assertTrue(
                    firstReconnect.await(10, TimeUnit.SECONDS),
                    "Expected onReconnect to fire while the first callback was blocked"
            );

            SseReconnectEvent event = reconnects.get(0);
            assertEquals(SseReconnectCause.BUFFER_FULL, event.cause());
            assertEquals(Optional.of("message"), event.eventType());
            assertEquals(1, event.attempt());
            assertFalse(event.delay().isNegative());
        } finally {
            releaseFirstEvent.countDown();
            listener.close();
        }
    }

    @Test
    void onReconnectHandlerThrows_doesNotStopTheReconnectLoop() throws InterruptedException {
        AtomicInteger reconnectCount = new AtomicInteger(0);
        // Waiting for a SECOND invocation, not just the first, is what actually proves the
        // reader thread survived its own onReconnect handler throwing — same reasoning
        // already used for onErrorHandlerThrows_.../onDroppedHandlerThrows_... above.
        CountDownLatch secondReconnect = new CountDownLatch(2);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message()
                                .contains("The SSE onReconnect observer threw an unexpected exception."))
                        .build()
                )
                .build()) {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/this-path-does-not-exist-for-on-reconnect-throws")
                    // Bounded exponential rather than fixed — this test only needs two
                    // onReconnect invocations; kept consistent with the other
                    // reconnect-loop tests above.
                    .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)))
                    .onEvent("message", SseHandler.of(message -> {
                    }))
                    .observer(new SseListenerObserver() {
                        @Override
                        public void onReconnect(SseReconnectEvent event) {
                            reconnectCount.incrementAndGet();
                            secondReconnect.countDown();
                            throw new IllegalStateException("Simulated onReconnect handler failure.");
                        }
                    })
                    .build();

            try {
                listener.connectAsync();

                assertTrue(secondReconnect.await(10, TimeUnit.SECONDS));
                verifier.assertExpectations(Duration.ofSeconds(10));
                assertTrue(
                        reconnectCount.get() >= 2,
                        "Expected the reconnect loop to survive the onReconnect handler throwing"
                );
            } finally {
                listener.close();
            }
        }
    }
}

