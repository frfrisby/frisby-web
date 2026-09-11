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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link SseListenerObserver#onEventReceived(SseEventReceived)} and
 * {@link SseListenerObserver#onEventProcessed(SseEventProcessed)} — the real-time,
 * per-event telemetry callbacks, as distinct from {@link SseListener#pipelineStats()}'s
 * on-demand snapshot (covered separately in {@code ClientSsePipelineStatsTest}) and
 * {@link SseListenerObserver#onReconnect(SseReconnectEvent)} (covered in
 * {@code ClientSseReconnectTest}).
 * <p>
 * Every test uses a unique {@code channel} query parameter so the resource's in-memory
 * per-channel event log does not leak state between tests. Every test — both
 * {@code onEventReceived} and {@code onEventProcessed} — counts down its own dedicated
 * latch from inside the observer callback itself, rather than reusing the handler's own
 * delivery latch. This matters for two distinct reasons depending on which callback is
 * under test:
 * <ul>
 *   <li>{@code onEventProcessed} is invoked strictly after the handler callback returns
 *       (see {@code DefaultSseListener.dispatchSafely}) — on the <em>same</em> pipeline
 *       worker thread — so racing the test thread's own assertions against the handler's
 *       completion latch would leave a window where {@code onEventProcessed} has not
 *       fired yet.</li>
 *   <li>{@code onEventReceived} fires from the reader thread as part of
 *       {@code Buffer.post()}'s posted-notification callback, strictly <em>before</em>
 *       the item is handed to the pipeline's worker thread for dispatch — but that
 *       worker thread runs concurrently and independently once the item is enqueued.
 *       There is no happens-before relationship guaranteeing the reader thread's
 *       notification completes before the worker thread finishes invoking the handler
 *       callback, so waiting on the handler's own latch is a genuine race that can fail
 *       under load even though it usually passes when run in isolation.</li>
 * </ul>
 */
class ClientSseTelemetryTest {
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
                .components(TestLogging.forClass(ClientSseTelemetryTest.class))
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
    void onEventReceived_firesForHandledEvent_withRegisteredAsPresent() throws InterruptedException {
        List<SseEventReceived> received = new CopyOnWriteArrayList<>();
        CountDownLatch receivedLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "telemetry-received-handled")
                .parameter("count", "1")
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventReceived(SseEventReceived event) {
                        received.add(event);
                        receivedLatch.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(receivedLatch.await(10, TimeUnit.SECONDS));
            assertEquals(1, received.size());

            SseEventReceived event = received.get(0);
            assertEquals(Optional.of("message"), event.event());
            assertEquals(Optional.of("message"), event.registeredAs());
            assertEquals(Optional.of("1"), event.id());
            assertTrue(null != event.receivedAt());
        } finally {
            listener.close();
        }
    }

    @Test
    void onEventReceived_forNamedButUnhandledEvent_hasEventPresentAndRegisteredAsEmpty() throws InterruptedException {
        List<SseEventReceived> received = new CopyOnWriteArrayList<>();
        CountDownLatch receivedLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "telemetry-received-named-unhandled")
                .parameter("count", "1")
                .onUnhandledEvent(message -> {
                })
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventReceived(SseEventReceived event) {
                        received.add(event);
                        receivedLatch.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(receivedLatch.await(10, TimeUnit.SECONDS));
            assertEquals(1, received.size());
            assertEquals(Optional.of("message"), received.get(0).event());
            assertEquals(Optional.empty(), received.get(0).registeredAs());
        } finally {
            listener.close();
        }
    }

    @Test
    void onEventReceived_forUnnamedEvent_hasEventAndRegisteredAsBothEmpty() throws InterruptedException {
        List<SseEventReceived> received = new CopyOnWriteArrayList<>();
        CountDownLatch receivedLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "telemetry-received-unnamed")
                .parameter("count", "1")
                .parameter("includeEventField", "false")
                .onUnhandledEvent(message -> {
                })
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventReceived(SseEventReceived event) {
                        received.add(event);
                        receivedLatch.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(receivedLatch.await(10, TimeUnit.SECONDS));
            assertEquals(1, received.size());
            assertEquals(Optional.empty(), received.get(0).event());
            assertEquals(Optional.empty(), received.get(0).registeredAs());
        } finally {
            listener.close();
        }
    }

    @Test
    void onEventProcessed_firesOnSuccess_withSucceededTrueAndNonNegativeTimings() throws InterruptedException {
        List<SseEventProcessed> processed = new CopyOnWriteArrayList<>();
        CountDownLatch processedLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "telemetry-processed-success")
                .parameter("count", "1")
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventProcessed(SseEventProcessed event) {
                        processed.add(event);
                        processedLatch.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(processedLatch.await(10, TimeUnit.SECONDS));
            assertEquals(1, processed.size());

            SseEventProcessed event = processed.get(0);
            assertTrue(event.succeeded());
            assertEquals(Optional.of("message"), event.registeredAs());
            assertEquals(Optional.of("1"), event.id());
            assertFalse(event.totalLatency().isNegative());
            assertFalse(event.processingDuration().isNegative());
        } finally {
            listener.close();
        }
    }

    @Test
    void onEventProcessed_firesOnFailure_withSucceededFalse_whenCallbackThrows() throws InterruptedException {
        List<SseEventProcessed> processed = new CopyOnWriteArrayList<>();
        AtomicReference<SseErrorEvent> capturedError = new AtomicReference<>();
        CountDownLatch processedLatch = new CountDownLatch(1);
        CountDownLatch errorLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "telemetry-processed-failure")
                .parameter("count", "1")
                .onEvent("message", SseHandler.of(message -> {
                    throw new IllegalStateException("Simulated handler failure.");
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventProcessed(SseEventProcessed event) {
                        processed.add(event);
                        processedLatch.countDown();
                    }

                    @Override
                    public void onError(SseErrorEvent error) {
                        capturedError.set(error);
                        errorLatch.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(processedLatch.await(10, TimeUnit.SECONDS));
            assertTrue(errorLatch.await(10, TimeUnit.SECONDS));

            assertEquals(1, processed.size());
            assertFalse(processed.get(0).succeeded());
            assertEquals(IllegalStateException.class, capturedError.get().cause().getClass());
        } finally {
            listener.close();
        }
    }

    @Test
    void onEventProcessedForBatch_onSuccess_firesOncePerItem_allSucceededTrue_withSharedProcessingDuration()
            throws InterruptedException {
        int totalEvents = 3;
        List<SseEventProcessed> processed = new CopyOnWriteArrayList<>();
        CountDownLatch processedLatch = new CountDownLatch(totalEvents);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "telemetry-processed-batch-success")
                .parameter("count", String.valueOf(totalEvents))
                .onEvent("message", SseBatchHandler.of((List<SseMessage<String>> messages) -> {
                }).batchSize(totalEvents))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventProcessed(SseEventProcessed event) {
                        processed.add(event);
                        processedLatch.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(processedLatch.await(10, TimeUnit.SECONDS));
            assertEquals(totalEvents, processed.size());
            assertTrue(processed.stream().allMatch(SseEventProcessed::succeeded));
            assertTrue(processed.stream().allMatch(e -> Optional.of("message").equals(e.registeredAs())));
            assertEquals(
                    1,
                    processed.stream().map(SseEventProcessed::processingDuration).distinct().count(),
                    "Expected every item delivered as part of the same batch invocation to share "
                            + "an identical processingDuration"
            );
        } finally {
            listener.close();
        }
    }

    @Test
    void onEventProcessedForBatch_onFailure_firesOncePerItem_allSucceededFalse() throws InterruptedException {
        int totalEvents = 3;
        List<SseEventProcessed> processed = new CopyOnWriteArrayList<>();
        CountDownLatch processedLatch = new CountDownLatch(totalEvents);
        CountDownLatch errorLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "telemetry-processed-batch-failure")
                .parameter("count", String.valueOf(totalEvents))
                .onEvent("message", SseBatchHandler.of((List<SseMessage<String>> messages) -> {
                    throw new IllegalStateException("Simulated whole-batch handler failure.");
                }).batchSize(totalEvents))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onEventProcessed(SseEventProcessed event) {
                        processed.add(event);
                        processedLatch.countDown();
                    }

                    @Override
                    public void onError(SseErrorEvent error) {
                        errorLatch.countDown();
                    }
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(processedLatch.await(10, TimeUnit.SECONDS));
            assertTrue(errorLatch.await(10, TimeUnit.SECONDS));

            assertEquals(totalEvents, processed.size());
            assertTrue(processed.stream().noneMatch(SseEventProcessed::succeeded));
        } finally {
            listener.close();
        }
    }
}

