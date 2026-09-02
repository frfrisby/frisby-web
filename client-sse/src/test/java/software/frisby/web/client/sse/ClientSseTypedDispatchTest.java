package software.frisby.web.client.sse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.TestLogging;
import software.frisby.web.test.TestResources;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunk 10 integration tests — typed dispatch end-to-end, closing three coverage gaps
 * confirmed via a JaCoCo review before this chunk started (see {@code temp/sse-plan.md}):
 * {@code Class<T>} deserialization, {@code GenericType<T>} deserialization, and a single
 * item's deserialization failure inside a batch not discarding the rest of the batch.
 * <p>
 * None of the earlier chunk-specific test files exercised real JSON deserialization —
 * {@code SseTestResource}'s {@code data} field was always the bare string {@code "event-N"}.
 * This file relies on the {@code payload}/{@code malformedEventId} query parameters added to
 * that resource specifically to unblock this chunk.
 * <p>
 * Every test uses a unique {@code channel} query parameter so the resource's in-memory
 * per-channel event log does not leak state between tests.
 */
class ClientSseTypedDispatchTest {
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
                .components(TestLogging.forClass(ClientSseTypedDispatchTest.class))
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
    void classTypedHandler_deserializesJsonObjectPayload() throws InterruptedException {
        List<TestItem> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "typed-class-dispatch")
                .parameter("payload", "object")
                .onEvent("message", SseHandler.of(TestItem.class, message -> {
                    received.add(message.body());
                    latch.countDown();
                }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(
                    List.of(new TestItem("event-1"), new TestItem("event-2"), new TestItem("event-3")),
                    received
            );
        } finally {
            listener.close();
        }
    }

    @Test
    void genericTypeHandler_deserializesJsonArrayPayload() throws InterruptedException {
        List<List<String>> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "typed-generic-dispatch")
                .parameter("payload", "array")
                .onEvent("message", SseHandler.of(new GenericType<List<String>>() {
                }, message -> {
                    received.add(message.body());
                    latch.countDown();
                }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of(List.of("event-1"), List.of("event-2"), List.of("event-3")), received);
        } finally {
            listener.close();
        }
    }

    @Test
    void genericTypeBatchHandler_deserializesJsonArrayPayload() throws InterruptedException {
        // Exercises DefaultSseListener.toMessage(RawSseEvent, SseBatchHandler)'s
        // genericType() branch specifically — the only other existing use of
        // SseBatchHandler.of(GenericType, ...) anywhere in this test suite is
        // DefaultSseListenerBuilderTest's genericallyTypedBatchHandler_... validation
        // test, which asserts onUnhandledEvent rejects it outright, so that handler is
        // never actually used to build a real pipeline. This is the only test that
        // exercises the branch through a real onEvent(String, SseBatchHandler)
        // registration and an actual deserialization.
        AtomicReference<List<List<String>>> deliveredBatch = new AtomicReference<>();
        CountDownLatch batchLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "typed-generic-batch-dispatch")
                .parameter("payload", "array")
                .parameter("count", "3")
                .onEvent("message", SseBatchHandler.of(new GenericType<List<String>>() {
                }, messages -> {
                    deliveredBatch.set(messages.stream().map(SseMessage::body).toList());
                    batchLatch.countDown();
                }).batchSize(3))
                .build();

        try {
            listener.connectAsync();

            assertTrue(batchLatch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of(List.of("event-1"), List.of("event-2"), List.of("event-3")), deliveredBatch.get());
        } finally {
            listener.close();
        }
    }

    @Test
    void oneMalformedItemInBatch_isOmittedWithoutDiscardingTheRestOfTheBatch() throws InterruptedException {
        AtomicReference<List<TestItem>> deliveredBatch = new AtomicReference<>();
        AtomicReference<SseErrorEvent> capturedError = new AtomicReference<>();
        CountDownLatch batchLatch = new CountDownLatch(1);
        CountDownLatch errorLatch = new CountDownLatch(1);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("SSE event deserialization failed."))
                        .build()
                )
                .build()) {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .parameter("channel", "typed-batch-partial-failure")
                    .parameter("payload", "object")
                    .parameter("count", "3")
                    .parameter("malformedEventId", "2")
                    .onEvent("message", SseBatchHandler.of(TestItem.class, messages -> {
                        deliveredBatch.set(messages.stream().map(SseMessage::body).toList());
                        batchLatch.countDown();
                    }).batchSize(3))
                    .onError(error -> {
                        capturedError.set(error);
                        errorLatch.countDown();
                    })
                    .build();

            try {
                listener.connectAsync();

                assertTrue(batchLatch.await(10, TimeUnit.SECONDS));
                assertTrue(errorLatch.await(10, TimeUnit.SECONDS));
                verifier.assertExpectations(Duration.ofSeconds(10));

                // Event 2's malformed data is dropped from the batch individually — the batch
                // still arrives with the two well-formed items, not discarded entirely.
                assertEquals(List.of(new TestItem("event-1"), new TestItem("event-3")), deliveredBatch.get());
                assertTrue(capturedError.get().message().isPresent());
                assertEquals("{not-valid-json", capturedError.get().message().get().body());
            } finally {
                listener.close();
            }
        }
    }

    @Test
    void entireBatchMalformed_deliversNothingForThatBatch_butSubsequentBatchesStillArrive()
            throws InterruptedException {
        // batchSize(1) makes every event its own batch, so event 1 (malformed) becomes a
        // batch whose only item fails to deserialize — deserializeBatchSafely filters it
        // out, leaving a non-null but empty List<Delivery>, which is exactly the
        // deliveries.isEmpty() branch of DefaultSseListener.dispatchBatchSafely: nothing is
        // ever delivered for that batch, and the handler's callback is never invoked for
        // it. Event 2 (valid) still arrives normally as its own batch immediately after,
        // proving that an entirely-malformed batch doesn't wedge subsequent delivery.
        List<List<TestItem>> deliveredBatches = new CopyOnWriteArrayList<>();
        AtomicReference<SseErrorEvent> capturedError = new AtomicReference<>();
        CountDownLatch batchLatch = new CountDownLatch(1);
        CountDownLatch errorLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "typed-batch-entirely-malformed")
                .parameter("payload", "object")
                .parameter("count", "2")
                .parameter("malformedEventId", "1")
                .onEvent("message", SseBatchHandler.of(TestItem.class, messages -> {
                    deliveredBatches.add(messages.stream().map(SseMessage::body).toList());
                    batchLatch.countDown();
                }).batchSize(1))
                .onError(error -> {
                    capturedError.set(error);
                    errorLatch.countDown();
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(batchLatch.await(10, TimeUnit.SECONDS));
            assertTrue(errorLatch.await(10, TimeUnit.SECONDS));

            // Only event 2's batch was ever delivered — event 1's entirely-malformed batch
            // never invoked the callback at all, rather than invoking it with an empty list.
            assertEquals(1, deliveredBatches.size());
            assertEquals(List.of(new TestItem("event-2")), deliveredBatches.get(0));
            assertTrue(capturedError.get().message().isPresent());
            assertEquals("{not-valid-json", capturedError.get().message().get().body());
        } finally {
            listener.close();
        }
    }

    @Test
    void malformedSingleEventPayload_isDroppedWithoutStoppingSubsequentDelivery() throws InterruptedException {
        List<TestItem> received = new CopyOnWriteArrayList<>();
        AtomicReference<SseErrorEvent> capturedError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);
        CountDownLatch errorLatch = new CountDownLatch(1);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("SSE event deserialization failed."))
                        .build()
                )
                .build()) {
            // Exercises DefaultSseListener.deserializeSafely(RawSseEvent, SseHandler) — the
            // single-event overload's catch block — as distinct from the SseBatchHandler
            // overload exercised by oneMalformedItemInBatch... above. A single event whose
            // data fails to deserialize is simply not delivered (Transform's null-return
            // convention drops it, per buildHandlerPipeline), rather than "dropped from a
            // batch alongside surviving siblings" — there is no batch here to survive.
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .parameter("channel", "typed-single-event-malformed-payload")
                    .parameter("payload", "object")
                    .parameter("count", "3")
                    .parameter("malformedEventId", "2")
                    .onEvent("message", SseHandler.of(TestItem.class, message -> {
                        received.add(message.body());
                        latch.countDown();
                    }))
                    .onError(error -> {
                        capturedError.set(error);
                        errorLatch.countDown();
                    })
                    .build();

            try {
                listener.connectAsync();

                assertTrue(latch.await(10, TimeUnit.SECONDS));
                assertTrue(errorLatch.await(10, TimeUnit.SECONDS));
                verifier.assertExpectations(Duration.ofSeconds(10));

                // Event 2's malformed data never reaches the handler at all; events 1 and 3
                // still arrive normally.
                assertEquals(List.of(new TestItem("event-1"), new TestItem("event-3")), received);
                assertTrue(capturedError.get().message().isPresent());
                assertEquals("{not-valid-json", capturedError.get().message().get().body());
            } finally {
                listener.close();
            }
        }
    }
}

