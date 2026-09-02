package software.frisby.web.server.sse;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSseEmitterTest {
    private static final String NULL_EVENT_MESSAGE = "The 'event' value is invalid. The value must not be null.";
    private static final String HEARTBEAT_COMMENT = "keep-alive";
    private static final String HEARTBEAT_FAILURE_MESSAGE = "Heartbeat send failed.";

    @Nested
    class Send {
        @Test
        void nullEvent_throwsNullValueException() {
            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    new CapturingSink(),
                    new CapturingSse(),
                    null
            );

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> emitter.send(null)
            );

            assertEquals(NULL_EVENT_MESSAGE, ex.getMessage());
        }

        @Test
        void sendSuccess_mapsWireFieldsAndCompletesFuture() {
            CapturingSink sink = new CapturingSink();

            try (DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    null
            )) {
                software.frisby.web.server.sse.SseEvent event = software.frisby.web.server.sse.SseEvent.builder()
                        .id("id-1")
                        .event("type-1")
                        .retry(Duration.ofMillis(250))
                        .data("payload")
                        .build();

                CompletableFuture<Void> future = emitter.send(event);

                assertDoesNotThrow(future::join);
                assertEquals(1, sink.sentEvents.size());

                OutboundSseEvent sent = sink.sentEvents.get(0);

                assertEquals("id-1", sent.getId());
                assertEquals("type-1", sent.getName());
                assertTrue(sent.isReconnectDelaySet());
                assertEquals(250L, sent.getReconnectDelay());
                assertEquals(String.class, sent.getType());
                assertEquals("payload", sent.getData());
            }
        }

        @Test
        void sinkSendFailure_completesFutureExceptionally() {
            IllegalStateException failure = new IllegalStateException("send failed");
            CapturingSink sink = new CapturingSink();
            sink.sendResult = CompletableFuture.failedFuture(failure);

            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    null
            );

            CompletableFuture<Void> future = emitter.send(
                    software.frisby.web.server.sse.SseEvent.builder()
                            .data("payload")
                            .build()
            );

            CompletionException ex = assertThrows(
                    CompletionException.class,
                    future::join
            );

            assertSame(failure, ex.getCause());
        }

        @Test
        void sinkSendCompletionException_unwrapsToRootCause() {
            IllegalStateException rootCause = new IllegalStateException("root cause");
            CapturingSink sink = new CapturingSink();
            sink.sendResult = CompletableFuture.failedFuture(new CompletionException(rootCause));

            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    null
            );

            CompletableFuture<Void> future = emitter.send(
                    software.frisby.web.server.sse.SseEvent.builder()
                            .data("payload")
                            .build()
            );

            CompletionException ex = assertThrows(
                    CompletionException.class,
                    future::join
            );

            assertSame(rootCause, ex.getCause());
        }

        @Test
        void sinkSendCompletionExceptionWithNullCause_preservesCompletionException() {
            CompletionException completionException = new CompletionException((Throwable) null);
            CapturingSink sink = new CapturingSink();
            sink.sendResult = CompletableFuture.failedFuture(completionException);

            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    null
            );

            CompletionException ex = assertThrows(
                    CompletionException.class,
                    () -> emitter.send(
                            software.frisby.web.server.sse.SseEvent.builder()
                                    .data("payload")
                                    .build()
                    ).join()
            );

            assertSame(completionException, ex);
            assertEquals(null, ex.getCause());
        }

        @Test
        void loggerAtInfo_sendDoesNotLogTraceEvent() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(DefaultSseEmitter.class, System.Logger.Level.INFO)
                    .build();
                 DefaultSseEmitter emitter = new DefaultSseEmitter(
                         new CapturingSink(),
                         new CapturingSse(),
                         null
                 )) {
                emitter.send(
                        software.frisby.web.server.sse.SseEvent.builder()
                                .id("id-1")
                                .event("type-1")
                                .data("payload")
                                .build()
                ).join();

                assertEquals(0, verifier.traceCount());
            }
        }

        @Test
        void outboundBuilderFailure_returnsFailedFuture() {
            IllegalArgumentException failure = new IllegalArgumentException("builder failed");
            CapturingSse sse = new CapturingSse();
            sse.newEventBuilderException = failure;

            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    new CapturingSink(),
                    sse,
                    null
            );

            CompletableFuture<Void> future = emitter.send(
                    software.frisby.web.server.sse.SseEvent.builder()
                            .data("payload")
                            .build()
            );

            CompletionException ex = assertThrows(
                    CompletionException.class,
                    future::join
            );

            assertSame(failure, ex.getCause());
        }
    }

    @Nested
    class Lifecycle {
        @Test
        void isOpen_reflectsSinkClosedState() {
            CapturingSink sink = new CapturingSink();

            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    null
            );

            assertTrue(emitter.isOpen());

            sink.closed = true;

            assertFalse(emitter.isOpen());
        }

        @Test
        void close_isIdempotentAndClosesSinkOnce() {
            CapturingSink sink = new CapturingSink();

            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    null
            );

            emitter.close();
            emitter.close();

            assertEquals(1, sink.closeCalls);
            assertTrue(sink.closed);
        }

        @Test
        void heartbeatEnabled_sendsKeepAliveComments() throws InterruptedException {
            CapturingSink sink = new CapturingSink();
            sink.awaitedSends = new CountDownLatch(1);

            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    Duration.ofMillis(20)
            );

            try (emitter) {
                assertTrue(sink.awaitSend(Duration.ofSeconds(2)));
                assertTrue(sink.sentEvents.stream().anyMatch(event -> HEARTBEAT_COMMENT.equals(event.getComment())));
            } finally {
                sink.awaitedSends = null;
            }
        }

        @Test
        void heartbeatEnabledAndSinkClosed_doesNotSendHeartbeat() throws InterruptedException {
            CapturingSink sink = new CapturingSink();
            sink.closed = true;
            sink.awaitedSends = new CountDownLatch(1);

            try (DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    Duration.ofMillis(20)
            )) {
                assertFalse(sink.awaitSend(Duration.ofMillis(150)));
                assertTrue(sink.sentEvents.isEmpty());
            } finally {
                sink.awaitedSends = null;
            }
        }

        @Test
        void heartbeatSendThrows_logsDebugMessageWithCause() throws InterruptedException {
            RuntimeException cause = new RuntimeException("Simulated heartbeat send failure.");
            CapturingSink sink = new CapturingSink();
            sink.sendException = cause;

            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(DefaultSseEmitter.class, System.Logger.Level.DEBUG)
                    .expect(LogExpectation.builder()
                            .logger(DefaultSseEmitter.class)
                            .level(System.Logger.Level.DEBUG)
                            .predicate(e -> e.message().contains(HEARTBEAT_FAILURE_MESSAGE) && cause == e.thrown())
                            .build()
                    )
                    .build();
                 DefaultSseEmitter emitter = new DefaultSseEmitter(
                         sink,
                         new CapturingSse(),
                         Duration.ofMillis(20)
                 )) {
                verifier.assertExpectations(Duration.ofSeconds(2));
            }
        }

        @Test
        void loggerAtWarning_infoLifecycleMessagesAreNotLogged() {
            try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                    .configure(DefaultSseEmitter.class, System.Logger.Level.WARNING)
                    .build();
                 DefaultSseEmitter emitter = new DefaultSseEmitter(
                         new CapturingSink(),
                         new CapturingSse(),
                         null
                 )) {
                assertEquals(0, verifier.infoCount());
            }
        }

        @Test
        void closeWithSlowSink_doesNotSendHeartbeatDuringClose() throws Exception {
            CapturingSink sink = new CapturingSink();
            sink.closeDelay = Duration.ofMillis(150);
            sink.closeStarted = new CountDownLatch(1);

            DefaultSseEmitter emitter = new DefaultSseEmitter(
                    sink,
                    new CapturingSse(),
                    Duration.ofMillis(20)
            );

            ExecutorService executor = Executors.newSingleThreadExecutor();

            try {
                Future<?> closeFuture = executor.submit(emitter::close);

                assertTrue(sink.closeStarted.await(2, TimeUnit.SECONDS));

                int sentBeforeWait = sink.sentEvents.size();

                Thread.sleep(120);

                int sentAfterWait = sink.sentEvents.size();

                closeFuture.get(2, TimeUnit.SECONDS);

                assertEquals(sentBeforeWait, sentAfterWait);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static final class CapturingSink implements SseEventSink {
        private final List<OutboundSseEvent> sentEvents;
        private CompletionStage<?> sendResult;
        private RuntimeException sendException;
        private CountDownLatch awaitedSends;
        private CountDownLatch closeStarted;
        private Duration closeDelay;
        private boolean closed;
        private int closeCalls;

        private CapturingSink() {
            this.sentEvents = new CopyOnWriteArrayList<>();
            this.sendResult = CompletableFuture.completedFuture(null);
            this.sendException = null;
            this.awaitedSends = null;
            this.closeStarted = null;
            this.closeDelay = Duration.ZERO;
            this.closed = false;
            this.closeCalls = 0;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public CompletionStage<?> send(OutboundSseEvent event) {
            if (null != sendException) {
                throw sendException;
            }

            sentEvents.add(event);

            if (null != awaitedSends) {
                awaitedSends.countDown();
            }

            return sendResult;
        }

        @Override
        public void close() {
            if (null != closeStarted) {
                closeStarted.countDown();
            }

            if (null != closeDelay && !closeDelay.isZero()) {
                try {
                    Thread.sleep(closeDelay.toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            this.closeCalls++;
            this.closed = true;
        }

        private boolean awaitSend(Duration timeout) throws InterruptedException {
            return null != awaitedSends
                    && awaitedSends.await(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private static final class CapturingSse implements Sse {
        private RuntimeException newEventBuilderException;

        @Override
        public OutboundSseEvent.Builder newEventBuilder() {
            if (null != newEventBuilderException) {
                throw newEventBuilderException;
            }

            return new CapturingOutboundSseEventBuilder();
        }

        @Override
        public SseBroadcaster newBroadcaster() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CapturingOutboundSseEventBuilder implements OutboundSseEvent.Builder {
        private String id;
        private String name;
        private String comment;
        private long reconnectDelay;
        private boolean reconnectDelaySet;
        private Class<?> type;
        private Type genericType;
        private MediaType mediaType;
        private Object data;

        private CapturingOutboundSseEventBuilder() {
            this.id = null;
            this.name = null;
            this.comment = null;
            this.reconnectDelay = jakarta.ws.rs.sse.SseEvent.RECONNECT_NOT_SET;
            this.reconnectDelaySet = false;
            this.type = null;
            this.genericType = null;
            this.mediaType = null;
            this.data = null;
        }

        @Override
        public OutboundSseEvent.Builder id(String id) {
            this.id = id;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder reconnectDelay(long milliseconds) {
            this.reconnectDelay = milliseconds;
            this.reconnectDelaySet = true;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder mediaType(MediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder data(Class type, Object data) {
            this.type = type;
            this.genericType = type;
            this.data = data;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder data(GenericType type, Object data) {
            this.type = Object.class;
            this.genericType = type.getType();
            this.data = data;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder data(Object data) {
            this.data = data;
            this.type = null == data ? Object.class : data.getClass();
            this.genericType = this.type;
            return this;
        }

        @Override
        public OutboundSseEvent build() {
            return new CapturingOutboundSseEvent(
                    id,
                    name,
                    comment,
                    reconnectDelay,
                    reconnectDelaySet,
                    type,
                    genericType,
                    mediaType,
                    data
            );
        }
    }

    private static final class CapturingOutboundSseEvent implements OutboundSseEvent {
        private final String id;
        private final String name;
        private final String comment;
        private final long reconnectDelay;
        private final boolean reconnectDelaySet;
        private final Class<?> type;
        private final Type genericType;
        private final MediaType mediaType;
        private final Object data;

        private CapturingOutboundSseEvent(
                String id,
                String name,
                String comment,
                long reconnectDelay,
                boolean reconnectDelaySet,
                Class<?> type,
                Type genericType,
                MediaType mediaType,
                Object data
        ) {
            this.id = id;
            this.name = name;
            this.comment = comment;
            this.reconnectDelay = reconnectDelay;
            this.reconnectDelaySet = reconnectDelaySet;
            this.type = type;
            this.genericType = genericType;
            this.mediaType = mediaType;
            this.data = data;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getComment() {
            return comment;
        }

        @Override
        public long getReconnectDelay() {
            return reconnectDelay;
        }

        @Override
        public boolean isReconnectDelaySet() {
            return reconnectDelaySet;
        }

        @Override
        public Class<?> getType() {
            return type;
        }

        @Override
        public Type getGenericType() {
            return genericType;
        }

        @Override
        public MediaType getMediaType() {
            return mediaType;
        }

        @Override
        public Object getData() {
            return data;
        }
    }
}






