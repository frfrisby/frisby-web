package software.frisby.web.client.sse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.core.concurrency.NamedExecutorService;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the reader-thread/executor lifecycle hardening pass — see
 * {@code temp/sse-plan.md}'s dedicated section. Covers two shutdown paths that were
 * never previously exercised: a {@link BufferFullPolicy#BLOCK} post interrupted by
 * {@link SseListener#close()} racing a blocked reader, and a caller shutting down their
 * own supplied {@link ExecutorService} without ever calling {@code close()} first.
 * <p>
 * Every test uses a unique {@code channel} query parameter so the resource's in-memory
 * per-channel event log does not leak state between tests.
 */
class ClientSseShutdownTest {
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
                .components(TestLogging.forClass(ClientSseShutdownTest.class))
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
    void blockPolicyPostInterruptedDuringClose_doesNotAdvanceLastEventIdPastTheLostEvent()
            throws InterruptedException {
        String channel = "block-post-interrupted-during-close";
        CountDownLatch firstEventProcessing = new CountDownLatch(1);
        CountDownLatch releaseFirstEvent = new CountDownLatch(1);

        // capacity(1) — the default BufferFullPolicy.BLOCK — means the reader thread's
        // second post() call cannot possibly succeed until the first event's handler
        // (blocked below, holding the buffer's sole capacity permit) returns. This
        // deterministically forces the reader thread into AsyncBuffer.post()'s blocking
        // capacityGate.acquire() call for event 2 well before close() is invoked, rather
        // than relying on a race that might not reproduce every run.
        SseListener firstListener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", channel)
                .parameter("count", "5")
                // The first event's handler is deliberately never released before
                // close() is called below, so its pipeline cannot possibly finish
                // draining in time — a short closeTimeout keeps this test fast instead
                // of waiting out the 30s default, and is exactly the scenario this
                // setting exists for.
                .closeTimeout(Duration.ofMillis(300))
                .onEvent("message", SseHandler.of(message -> {
                    firstEventProcessing.countDown();

                    try {
                        releaseFirstEvent.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).capacity(1))
                .build();

        try {
            firstListener.connectAsync();

            assertTrue(firstEventProcessing.await(10, TimeUnit.SECONDS), "Expected the first event to be delivered");

            // Give the reader thread time to read event 2 off the wire and block trying
            // to post it into the still-occupied capacity-1 buffer, before close() races it.
            Thread.sleep(300);

            // Before the fix, postWithPolicy's BLOCK branch discarded post()'s return
            // value and always reported success — meaning the interrupted post for event
            // 2 would still (incorrectly) advance lastEventId to "2", permanently losing
            // it. This close() call interrupts that exact blocked post via
            // Future.cancel(true).
            firstListener.close();
        } finally {
            releaseFirstEvent.countDown();
        }

        // A brand new listener seeded with lastEventId("1") — the only id the reader
        // thread ever actually confirmed delivering — should still receive events 2
        // through 5 on reconnect. If the bug were still present, lastEventId would have
        // already advanced to "2" before close() interrupted the post, and this second
        // listener would only ever see events 3 through 5.
        List<String> secondPassReceived = new CopyOnWriteArrayList<>();
        CountDownLatch secondPassLatch = new CountDownLatch(4);

        SseListener secondListener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", channel)
                .parameter("count", "5")
                .lastEventId("1")
                .onEvent("message", SseHandler.of(message -> {
                    secondPassReceived.add(message.body());
                    secondPassLatch.countDown();
                }))
                .build();

        try {
            secondListener.connectAsync();

            assertTrue(secondPassLatch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("event-2", "event-3", "event-4", "event-5"), secondPassReceived);
        } finally {
            secondListener.close();
        }
    }

    @Test
    void callerSuppliedExecutorAlreadyShutDown_closeReturnsWithinCloseTimeout_insteadOfHangingForever()
            throws InterruptedException {
        NamedExecutorService customExecutor = NamedExecutorService.builder()
                .threadPrefix("shutdown-test-executor")
                .build();

        CountDownLatch firstEventDelivered = new CountDownLatch(1);
        Duration closeTimeout = Duration.ofMillis(300);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "caller-executor-shutdown-independently")
                .parameter("count", "1")
                .executor(customExecutor)
                .closeTimeout(closeTimeout)
                .onEvent("message", SseHandler.of(message -> firstEventDelivered.countDown()))
                .build();

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("did not finish draining"))
                        .build()
                )
                .build()) {
            listener.connectAsync();

            assertTrue(
                    firstEventDelivered.await(10, TimeUnit.SECONDS),
                    "Expected at least one event to confirm the pipeline is actually live on customExecutor"
            );

            // Bypasses SseListener.close() entirely — simulating a caller who shuts down
            // their own supplied executor as part of their own application's shutdown
            // sequence, unaware that close() must be called first. Per frisby-core
            // 1.4.1's own (correct) behavior, a pipeline worker killed this way never
            // resolves its completion() future — before this hardening pass, close()'s
            // unbounded awaitCompletion() would have blocked forever here.
            customExecutor.shutdownNow();

            Instant before = Instant.now();
            listener.close();
            Duration elapsed = Duration.between(before, Instant.now());

            // Two pipelines ("message" handler + the unhandled-event fallback) each
            // independently bounded to closeTimeout — generous upper bound accounts for
            // both, plus scheduling overhead, without asserting an exact figure.
            assertTrue(
                    elapsed.compareTo(closeTimeout.multipliedBy(4)) < 0,
                    "Expected close() to return well within a small multiple of closeTimeout, took " + elapsed
            );

            verifier.assertExpectations(Duration.ofSeconds(10));
        }
    }
}


