package software.frisby.web.client.sse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.core.concurrency.NamedExecutorService;
import software.frisby.web.client.Client;
import software.frisby.web.client.security.SecurityProvider;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void connectAsyncCalledTwice_secondCallIsANoOp() throws InterruptedException {
        List<String> received = new CopyOnWriteArrayList<>();
        AtomicInteger securityInvocations = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        SecurityProvider countingProvider = request -> securityInvocations.incrementAndGet();

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "connect-async-twice-is-a-no-op")
                .security(countingProvider)
                .onEvent("message", SseHandler.of(message -> {
                    received.add(message.body());
                    latch.countDown();
                }))
                .build();

        try {
            listener.connectAsync();
            // A second call while already started must be a pure no-op — no second reader
            // thread, no second connection attempt, and critically, no attempt to rebuild
            // the dispatch pipelines (which would throw, since Pipeline construction is not
            // meant to be repeated) or replace the already-running reader thread.
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("event-1", "event-2", "event-3"), received);
            assertEquals(1, securityInvocations.get(), "Expected only one connection attempt in total");
            assertTrue(listener.isOpen());

            // isOpen() is started.get() && !closed.get() — every other test in this suite
            // only ever observes it before connectAsync() (started == false) or while open
            // (started == true, closed == false). Closing here and re-checking is what
            // actually exercises the started == true / closed == true combination, the one
            // isOpen() outcome nothing else in this module covered.
            listener.close();
            assertFalse(listener.isOpen());
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
    void onUnhandledEventWithFullConfigHandler_appliesTuningAndDeliversEvents() throws InterruptedException {
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "unhandled-full-config-handler")
                .onUnhandledEvent(SseHandler.of(message -> {
                    received.add(message.body());
                    latch.countDown();
                }).capacity(4))
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
    void noUnhandledHandlerRegisteredAtAll_defaultNoOpFallbackSwallowsEventsSilently() throws InterruptedException {
        // No .onUnhandledEvent(...) call at all — DefaultSseListener.buildUnhandledPipeline()
        // falls back to a plain no-op SseHandler in that case. Every other unhandled-event
        // scenario in this suite registers onUnhandledEvent explicitly, so that fallback
        // lambda's own body was never actually invoked by any existing test.
        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "no-unhandled-handler-registered-at-all")
                .parameter("includeEventField", "false")
                .onEvent("message", SseHandler.of(message -> { }))
                .build();

        try {
            listener.connectAsync();

            // No latch to await — the fallback handler intentionally does nothing
            // observable. close() below blocks on the fallback pipeline's own
            // awaitCompletion(), so a brief sleep first just gives the reader thread time
            // to actually post all three events (which all lack an event field, per
            // includeEventField=false) into that pipeline beforehand.
            Thread.sleep(500);
        } finally {
            assertDoesNotThrow(listener::close);
        }
    }

    @Test
    void customExecutor_isUsedForDispatch_andIsNotShutDownByClose() throws InterruptedException {
        NamedExecutorService customExecutor = NamedExecutorService.builder()
                .threadPrefix("custom-sse-executor")
                .build();

        List<String> threadNames = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "custom-executor")
                .executor(customExecutor)
                .onEvent("message", SseHandler.of(message -> {
                    threadNames.add(Thread.currentThread().getName());
                    latch.countDown();
                }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertTrue(
                    threadNames.stream().allMatch(name -> name.startsWith("custom-sse-executor")),
                    "Expected dispatch to run on the caller-supplied executor's threads, saw " + threadNames
            );
        } finally {
            listener.close();
        }

        // The connection owns and shuts down its own default NamedExecutorService in
        // close(), but a caller-supplied one is a shared resource the caller may still be
        // using elsewhere — close() must never shut it down on the caller's behalf.
        assertFalse(
                customExecutor.isShutdown(),
                "Expected a caller-supplied executor to not be shut down by SseListener.close()"
        );

        customExecutor.shutdown();
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
    void callbackExceptionWithNoOnErrorHandlerRegistered_doesNotStopSubsequentDelivery_andLogsOnlyOnce()
            throws InterruptedException {
        // No .onError(...) call at all — exercises DefaultSseListener.notify()'s
        // null == errorHandler guard, which no other test does (every other error-path
        // test in this suite registers onError). If that guard were ever removed,
        // errorHandler.accept(event) would NPE on the null field — an exception the
        // surrounding try/catch would still swallow and misreport as "the onError handler
        // threw," which is exactly what the warningCount() assertion below rules out: only
        // dispatchSafely's own "SSE handler callback failed." warning should ever appear,
        // never a second, spurious one about the (non-existent) onError handler.
        AtomicInteger deliveryCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(DefaultSseListener.class)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("SSE handler callback failed."))
                        .build()
                )
                .build()) {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .parameter("channel", "callback-exception-no-onerror-registered")
                    .onEvent("message", SseHandler.of(message -> {
                        int count = deliveryCount.incrementAndGet();
                        latch.countDown();

                        if (1 == count) {
                            throw new IllegalStateException("Simulated handler failure.");
                        }
                    }))
                    .build();

            try {
                listener.connectAsync();

                assertTrue(latch.await(10, TimeUnit.SECONDS));
                verifier.assertExpectations(Duration.ofSeconds(10));
                assertEquals(3, deliveryCount.get());
                assertEquals(1, verifier.warningCount(), "Expected no spurious onError-handler-threw log entry");
            } finally {
                listener.close();
            }
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

    @Test
    void onUnhandledEventCallbackException_doesNotStopSubsequentDelivery() throws InterruptedException {
        AtomicInteger deliveryCount = new AtomicInteger(0);
        AtomicReference<SseErrorEvent> capturedError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(3);
        CountDownLatch errorLatch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/sse/stream")
                .parameter("channel", "unhandled-callback-exception-does-not-kill-stream")
                .parameter("includeEventField", "false")
                .onUnhandledEvent(message -> {
                    int count = deliveryCount.incrementAndGet();
                    latch.countDown();

                    if (1 == count) {
                        throw new IllegalStateException("Simulated unhandled-event handler failure.");
                    }
                })
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
}

