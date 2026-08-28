package software.frisby.web.client.sse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunk 7 integration tests — single-event delivery ({@code onEvent}, no batching, no
 * reconnect loop) against {@code SseTestResource} in {@code test-support}, the
 * hand-written {@code text/event-stream} resource standing in for the not-yet-built
 * {@code server-sse} module (see {@code temp/sse-plan.md}, Chunk 3).
 * <p>
 * Every test uses a unique {@code channel} query parameter so the resource's in-memory
 * per-channel event log does not leak state between tests.
 */
class ClientSseSingleEventTest {
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
                .components(TestLogging.forClass(ClientSseSingleEventTest.class))
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
    void threeEvents_deliveredInOrderToRawHandler() throws InterruptedException {
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "single-event-in-order")
                .onEvent("message", SseHandler.of(message -> {
                    received.add(message.body());
                    latch.countDown();
                }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("event-1", "event-2", "event-3"), received);
        } finally {
            listener.close();
        }
    }

    @Test
    void noHandlerRegisteredForEventType_deliveredToUnhandledCatchAll() throws InterruptedException {
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "unhandled-catch-all")
                .onUnhandledEvent(message -> {
                    received.add(message.body());
                    latch.countDown();
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("event-1", "event-2", "event-3"), received);
        } finally {
            listener.close();
        }
    }

    @Test
    void eventWithNoEventFieldAtAll_routesToUnhandledEvent_notMessageHandler() throws InterruptedException {
        List<String> unhandled = new CopyOnWriteArrayList<>();
        List<String> messageHandlerCalls = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "no-event-field-not-message")
                .parameter("includeEventField", "false")
                .onEvent("message", SseHandler.of(message -> messageHandlerCalls.add(message.body())))
                .onUnhandledEvent(message -> {
                    unhandled.add(message.body());
                    latch.countDown();
                })
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("event-1", "event-2", "event-3"), unhandled);
            assertTrue(messageHandlerCalls.isEmpty());
        } finally {
            listener.close();
        }
    }

    @Test
    void eventWithExplicitMessageEventField_routesToRegisteredMessageHandler() throws InterruptedException {
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "explicit-message-event-field")
                .onEvent("message", SseHandler.of(message -> {
                    received.add(message.body());
                    latch.countDown();
                }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("event-1", "event-2", "event-3"), received);
        } finally {
            listener.close();
        }
    }

    @Test
    void callbackExceptionOnOneEvent_doesNotStopSubsequentDelivery() throws InterruptedException {
        AtomicInteger deliveryCount = new AtomicInteger(0);
        AtomicReference<SseErrorEvent> capturedError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(3);
        CountDownLatch errorLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "callback-exception-does-not-kill-stream")
                .onEvent("message", SseHandler.of(message -> {
                    int count = deliveryCount.incrementAndGet();
                    latch.countDown();

                    if (1 == count) {
                        throw new IllegalStateException("Simulated handler failure.");
                    }
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
            assertEquals(3, deliveryCount.get());
            assertEquals(IllegalStateException.class, capturedError.get().cause().getClass());
            assertTrue(capturedError.get().message().isPresent());
            assertEquals("event-1", capturedError.get().message().get().body());
        } finally {
            listener.close();
        }
    }

    @Test
    void onErrorHandlerThrows_doesNotStopSubsequentDelivery() throws InterruptedException {
        AtomicInteger deliveryCount = new AtomicInteger(0);
        AtomicInteger errorHandlerCallCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

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
                    .path("/sse/stream")
                    .parameter("channel", "on-error-handler-throws-does-not-kill-stream")
                    .onEvent("message", SseHandler.of(message -> {
                        int count = deliveryCount.incrementAndGet();
                        latch.countDown();

                        if (1 == count) {
                            throw new IllegalStateException("Simulated handler failure.");
                        }
                    }))
                    .onError(error -> {
                        errorHandlerCallCount.incrementAndGet();
                        throw new IllegalStateException("Simulated onError handler failure.");
                    })
                    .build();

            try {
                listener.connectAsync();

                assertTrue(latch.await(10, TimeUnit.SECONDS));
                verifier.assertExpectations(Duration.ofSeconds(10));
                assertEquals(3, deliveryCount.get());
                assertEquals(1, errorHandlerCallCount.get());
            } finally {
                listener.close();
            }
        }
    }
}

