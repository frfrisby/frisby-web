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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests — batched delivery ({@code onEventBatch}) against
 * {@code SseTestResource} in {@code test-support}, the hand-written
 * {@code text/event-stream} resource standing in for the not-yet-built
 * {@code server-sse} module (see {@code temp/sse-plan.md}, Chunk 3).
 * <p>
 * Every test uses a unique {@code channel} query parameter so the resource's in-memory
 * per-channel event log does not leak state between tests.
 */
class ClientSseBatchTest {
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
                .components(TestLogging.forClass(ClientSseBatchTest.class))
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
    void batchSizeReached_deliversOneFullBatch() throws InterruptedException {
        List<List<String>> batches = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "batch-size-reached")
                .parameter("count", "3")
                .batchSize(3)
                .onEventBatch("message", messages -> {
                    batches.add(messages.stream().map(SseMessage::body).toList());
                    latch.countDown();
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(1, batches.size());
            assertEquals(List.of("event-1", "event-2", "event-3"), batches.get(0));
        } finally {
            listener.close();
        }
    }

    @Test
    void batchTimeoutElapses_deliversPartialBatch() throws InterruptedException {
        List<List<String>> batches = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "batch-timeout-elapses")
                .parameter("count", "2")
                .batchSize(10)
                .batchTimeout(Duration.ofMillis(200))
                .onEventBatch("message", messages -> {
                    batches.add(messages.stream().map(SseMessage::body).toList());
                    latch.countDown();
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(1, batches.size());
            assertEquals(List.of("event-1", "event-2"), batches.get(0));
        } finally {
            listener.close();
        }
    }
}

