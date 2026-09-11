package software.frisby.web.client.sse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link SseListener#pipelineStats()} — the on-demand occupancy
 * snapshot, as distinct from the real-time per-event telemetry covered in
 * {@code ClientSseTelemetryTest}.
 * <p>
 * Every test uses a unique {@code channel} query parameter so the resource's in-memory
 * per-channel event log does not leak state between tests.
 */
class ClientSsePipelineStatsTest {
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
                .components(TestLogging.forClass(ClientSsePipelineStatsTest.class))
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
    void pipelineStats_beforeConnectAsync_throwsIllegalStateException() {
        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .onEvent("message", SseHandler.of(message -> {
                }))
                .build();

        try {
            assertThrows(IllegalStateException.class, listener::pipelineStats);
        } finally {
            listener.close();
        }
    }

    @Test
    void pipelineStats_reflectsRegisteredHandlerCapacityAndConcurrency() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "pipeline-stats-capacity")
                .parameter("count", "1")
                .onEvent("message", SseHandler.of(message -> latch.countDown()).capacity(50).concurrency(2))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            SsePipelineSnapshot snapshot = listener.pipelineStats();
            SsePipelineStats stats = snapshot.handlers().get("message");

            assertNotNull(stats);
            assertEquals(100, stats.capacity(), "Expected capacity() to be capacity x concurrency");
            assertEquals(2, stats.concurrency());
        } finally {
            listener.close();
        }
    }

    @Test
    void pipelineStats_includesUnhandledPipeline_evenWithoutExplicitRegistration() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "pipeline-stats-unhandled-fallback")
                .parameter("count", "1")
                .onEvent("message", SseHandler.of(message -> latch.countDown()))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            SsePipelineSnapshot snapshot = listener.pipelineStats();

            assertNotNull(snapshot.unhandled());
            assertEquals(
                    1024,
                    snapshot.unhandled().capacity(),
                    "Expected the fallback no-op unhandled handler's default capacity"
            );
            assertEquals(1, snapshot.unhandled().concurrency());
        } finally {
            listener.close();
        }
    }

    @Test
    void pipelineStats_reflectsInFlightItemWhileHandlerBlocked_thenDrainsAfterClose() throws InterruptedException {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "pipeline-stats-in-flight")
                .parameter("count", "1")
                .onEvent("message", SseHandler.of(message -> {
                    handlerEntered.countDown();

                    try {
                        releaseHandler.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).capacity(1))
                .build();

        try {
            listener.connectAsync();

            assertTrue(handlerEntered.await(10, TimeUnit.SECONDS));

            SsePipelineSnapshot whileBlocked = listener.pipelineStats();
            assertEquals(
                    1,
                    whileBlocked.handlers().get("message").inFlight(),
                    "Expected the single in-flight item to be reflected while the handler is blocked"
            );

            releaseHandler.countDown();
        } finally {
            releaseHandler.countDown();
            listener.close();
        }

        // close() blocks until the pipeline has fully drained, so this is a deterministic
        // check rather than a race against the worker thread's own bookkeeping.
        SsePipelineSnapshot afterClose = listener.pipelineStats();
        assertEquals(0, afterClose.handlers().get("message").inFlight());
    }
}

