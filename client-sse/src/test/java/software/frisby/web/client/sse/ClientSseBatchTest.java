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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests — batched delivery ({@code onEvent(String, SseBatchHandler)})
 * against {@code SseTestResource} in {@code test-support}, the hand-written
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
                .onEvent("message", SseBatchHandler.of((List<SseMessage<String>> messages) -> {
                    batches.add(messages.stream().map(SseMessage::body).toList());
                    latch.countDown();
                }).batchSize(3))
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
                .onEvent("message", SseBatchHandler.of((List<SseMessage<String>> messages) -> {
                    batches.add(messages.stream().map(SseMessage::body).toList());
                    latch.countDown();
                }).batchSize(10).batchTimeout(Duration.ofMillis(200)))
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

    @Test
    void onUnhandledEventBatchHandler_actuallyBuildsAPipelineAndDeliversBatches() throws InterruptedException {
        List<List<String>> batches = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Deliberately registers no "message" onEvent handler at all — every generated
        // event's default "message" event field has no entry in either handler map, so
        // dispatch() falls back to the catch-all pipeline via getOrDefault(). This is the
        // only way to actually exercise DefaultSseListener.buildUnhandledPipeline()'s
        // unhandledBatchHandler branch: DefaultSseListenerBuilderTest's own
        // onUnhandledEvent(SseBatchHandler) coverage only ever calls build(), never
        // connectAsync() — build() assembles the listener but performs no pipeline
        // construction at all, so that branch was never actually reached by any test.
        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "unhandled-event-batch-handler")
                .parameter("count", "3")
                .onUnhandledEvent(SseBatchHandler.of((List<SseMessage<String>> messages) -> {
                    batches.add(messages.stream().map(SseMessage::body).toList());
                    latch.countDown();
                }).batchSize(3))
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
    void mixingSingleEventAndBatchHandlersForDifferentEventTypes_bothDeliverCorrectly()
            throws InterruptedException {
        List<String> singleReceived = new CopyOnWriteArrayList<>();
        List<List<String>> batchesReceived = new CopyOnWriteArrayList<>();
        CountDownLatch singleLatch = new CountDownLatch(3);
        CountDownLatch batchLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "mixing-single-and-batch-handlers")
                .parameter("count", "6")
                .parameter("alternateEventTypes", "true")
                .onEvent("type-a", SseHandler.of(message -> {
                    singleReceived.add(message.body());
                    singleLatch.countDown();
                }))
                .onEvent("type-b", SseBatchHandler.of((List<SseMessage<String>> messages) -> {
                    batchesReceived.add(messages.stream().map(SseMessage::body).toList());
                    batchLatch.countDown();
                }).batchSize(3))
                .build();

        try {
            listener.connectAsync();

            assertTrue(singleLatch.await(10, TimeUnit.SECONDS));
            assertTrue(batchLatch.await(10, TimeUnit.SECONDS));

            // Odd ids (1, 3, 5) are type-a, delivered one at a time; even ids (2, 4, 6)
            // are type-b, grouped into a single batch of 3 by batchSize.
            assertEquals(List.of("event-1", "event-3", "event-5"), singleReceived);
            assertEquals(1, batchesReceived.size());
            assertEquals(List.of("event-2", "event-4", "event-6"), batchesReceived.get(0));
        } finally {
            listener.close();
        }
    }

    @Test
    void wholeBatchCallbackException_reportsEmptyMessageContext_andDoesNotStopSubsequentBatches()
            throws InterruptedException {
        List<List<String>> deliveredBatches = new CopyOnWriteArrayList<>();
        AtomicReference<SseErrorEvent> capturedError = new AtomicReference<>();
        CountDownLatch batchLatch = new CountDownLatch(2);
        CountDownLatch errorLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "whole-batch-callback-exception")
                .parameter("count", "4")
                .onEvent("message", SseBatchHandler.of((List<SseMessage<String>> messages) -> {
                    boolean isFirstBatch = messages.stream().anyMatch(m -> "event-1".equals(m.body()));

                    if (isFirstBatch) {
                        // Counted down before throwing, since nothing after this branch
                        // ever runs for this invocation.
                        batchLatch.countDown();
                        throw new IllegalStateException("Simulated whole-batch handler failure.");
                    }

                    // Countdown deliberately happens AFTER the list mutation below, not
                    // before — counting down first would let the main thread's
                    // batchLatch.await() return and read deliveredBatches on a race with
                    // this very add() call still pending on this worker thread, exactly the
                    // class of flaky assertion already found and fixed once in
                    // ClientSseReconnectTest's BufferFullPolicy.DROP test.
                    deliveredBatches.add(messages.stream().map(SseMessage::body).toList());
                    batchLatch.countDown();
                }).batchSize(2))
                .onError(error -> {
                    capturedError.set(error);
                    errorLatch.countDown();
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(batchLatch.await(10, TimeUnit.SECONDS));
            assertTrue(errorLatch.await(10, TimeUnit.SECONDS));

            // The first batch's callback threw before recording anything, so only the
            // second batch (events 3 and 4) ever made it into deliveredBatches.
            assertEquals(1, deliveredBatches.size());
            assertEquals(List.of("event-3", "event-4"), deliveredBatches.get(0));

            // A whole-batch callback exception is not attributable to any single event,
            // so its SseErrorEvent carries no message context — unlike a single-event
            // callback exception or a per-item deserialization failure, both of which do.
            assertEquals(IllegalStateException.class, capturedError.get().cause().getClass());
            assertTrue(capturedError.get().message().isEmpty());
        } finally {
            listener.close();
        }
    }
}

