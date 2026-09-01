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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunk 10 integration tests — {@code concurrency > 1} for both {@link SseHandler} and
 * {@link SseBatchHandler}, closing a coverage gap confirmed via a JaCoCo review before this
 * chunk started (see {@code temp/sse-plan.md}): the {@code Router}-wrapped fan-out path was
 * entirely unexercised by any earlier chunk's tests.
 * <p>
 * Every test uses a unique {@code channel} query parameter so the resource's in-memory
 * per-channel event log does not leak state between tests. Each handler callback sleeps
 * briefly so that, with a burst of events arriving far faster than one callback invocation
 * takes, multiple worker arms are actually overlapping in time rather than the whole burst
 * completing on a single arm before the next is ever used — this is what makes "more than
 * one distinct thread was used" an observable, non-flaky assertion rather than a coincidence
 * of scheduling.
 */
class ClientSseConcurrencyTest {
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
                .components(TestLogging.forClass(ClientSseConcurrencyTest.class))
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
    void onEventConcurrencyGreaterThanOne_invokesHandlerFromMultipleThreads() throws InterruptedException {
        int totalEvents = 40;
        Set<String> receivedThreads = ConcurrentHashMap.newKeySet();
        Set<String> receivedBodies = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(totalEvents);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "concurrency-onevent")
                .parameter("count", String.valueOf(totalEvents))
                .onEvent("message", SseHandler.of(message -> {
                    receivedThreads.add(Thread.currentThread().getName());
                    receivedBodies.add(message.body());
                    sleepBriefly();
                    latch.countDown();
                }).concurrency(4))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(20, TimeUnit.SECONDS));
            assertEquals(totalEvents, receivedBodies.size(), "Expected every event to still be delivered exactly once");
            assertTrue(
                    receivedThreads.size() > 1,
                    "Expected concurrency(4) to invoke the handler from more than one thread, saw " + receivedThreads
            );
        } finally {
            listener.close();
        }
    }

    @Test
    void onEventBatchConcurrencyGreaterThanOne_invokesHandlerFromMultipleThreads() throws InterruptedException {
        int totalEvents = 40;
        Set<String> receivedThreads = ConcurrentHashMap.newKeySet();
        List<String> receivedBodies = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(totalEvents);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "concurrency-onevent-batch")
                .parameter("count", String.valueOf(totalEvents))
                .onEvent("message", SseBatchHandler.of((List<SseMessage<String>> messages) -> {
                    receivedThreads.add(Thread.currentThread().getName());

                    for (SseMessage<String> message : messages) {
                        receivedBodies.add(message.body());
                        latch.countDown();
                    }

                    sleepBriefly();
                }).batchSize(2).concurrency(4))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(20, TimeUnit.SECONDS));
            assertEquals(totalEvents, receivedBodies.size(), "Expected every event to still be delivered exactly once");
            assertTrue(
                    receivedThreads.size() > 1,
                    "Expected concurrency(4) to invoke the batch handler from more than one thread, saw "
                            + receivedThreads
            );
        } finally {
            listener.close();
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

