package software.frisby.web.client.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.client.RetryDelay;
import software.frisby.web.serial.JsonSerializer;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.sse.SseEmitter;
import software.frisby.web.server.sse.SseEvent;
import software.frisby.web.server.sse.SseEvents;
import software.frisby.web.test.TestLogging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientServerSseCapstoneTest {
    private static final String TEXT_SINGLE_EVENT = "text-single";
    private static final String TEXT_MULTILINE_EVENT = "text-multiline";
    private static final String JSON_COMPACT_EVENT = "json-compact";
    private static final String JSON_PRETTY_EVENT = "json-pretty";

    private static Server server;
    private static Client client;

    @BeforeAll
    static void startServer() {
        JsonSerializer serializer = JacksonSerializer.builder().build();

        server = Server.builder()
                .configuration(configuration -> configuration
                        .port(0)
                        .host("localhost")
                        .serializer(serializer)
                )
                .resources(new CapstoneSseResource(serializer))
                .components(TestLogging.forClass(ClientServerSseCapstoneTest.class))
                .build();

        server.start();

        client = Client.builder()
                .configuration(configuration -> configuration
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .serializer(serializer)
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
    void serverSseAndClientSse_roundTripTypedAndRawAcrossReconnectsWithHeartbeatIgnored()
            throws InterruptedException {
        CapstoneSseResource.resetState();

        CountDownLatch delivered = new CountDownLatch(4);
        AtomicReference<String> singleLineText = new AtomicReference<>();
        AtomicReference<String> multilineText = new AtomicReference<>();
        AtomicReference<JsonPayload> compactJson = new AtomicReference<>();
        AtomicReference<JsonPayload> prettyJson = new AtomicReference<>();
        AtomicInteger unhandledCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        SseListener listener = SseListener.builder().client(client)
                .path("/capstone/stream")
                .reconnectDelay(RetryDelay.fixed(Duration.ofMillis(30)))
                .onEvent(TEXT_SINGLE_EVENT, SseHandler.of(message -> {
                    singleLineText.set(message.body());
                    delivered.countDown();
                }))
                .onEvent(TEXT_MULTILINE_EVENT, SseHandler.of(message -> {
                    multilineText.set(message.body());
                    delivered.countDown();
                }))
                .onEvent(JSON_COMPACT_EVENT, SseHandler.of(JsonPayload.class, message -> {
                    compactJson.set(message.body());
                    delivered.countDown();
                }))
                .onEvent(JSON_PRETTY_EVENT, SseHandler.of(JsonPayload.class, message -> {
                    prettyJson.set(message.body());
                    delivered.countDown();
                }))
                .onUnhandledEvent(message -> unhandledCount.incrementAndGet())
                .onError(error -> errorCount.incrementAndGet())
                .build();

        try (listener) {
            listener.connectAsync();

            assertTrue(delivered.await(20, TimeUnit.SECONDS));
            assertTrue(CapstoneSseResource.awaitAtLeastTwoConnections(Duration.ofSeconds(5)));

            List<String> lastEventIds = CapstoneSseResource.lastEventIdsSnapshot();

            assertNull(lastEventIds.get(0));
            assertEquals("2", lastEventIds.get(1));

            assertEquals("single-line", singleLineText.get());
            assertEquals("line-1\nline-2", multilineText.get());
            assertEquals(new JsonPayload("compact", 101, "alpha"), compactJson.get());
            assertEquals(new JsonPayload("pretty", 202, "beta"), prettyJson.get());

            assertTrue(CapstoneSseResource.prettyJsonContainedLineFeeds());
            assertEquals(0, unhandledCount.get());
            assertEquals(0, errorCount.get());
        }

        assertFalse(listener.isOpen());
    }

    private record JsonPayload(String type, int value, String note) {
    }

    @Path("/capstone")
    public static final class CapstoneSseResource {
        private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";
        private static final List<String> LAST_EVENT_IDS = new CopyOnWriteArrayList<>();
        private static CountDownLatch CONNECTIONS = new CountDownLatch(2);
        private static final AtomicBoolean PRETTY_JSON_CONTAINED_LINE_FEEDS = new AtomicBoolean(false);

        private static final List<OutboundRecord> OUTBOUND = List.of(
                OutboundRecord.text(1, TEXT_SINGLE_EVENT, "single-line"),
                OutboundRecord.text(2, TEXT_MULTILINE_EVENT, "line-1\nline-2"),
                OutboundRecord.payload(3, JSON_COMPACT_EVENT, new JsonPayload("compact", 101, "alpha")),
                OutboundRecord.payload(4, JSON_PRETTY_EVENT, new JsonPayload("pretty", 202, "beta"))
        );

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final JsonSerializer serializer;

        private CapstoneSseResource(JsonSerializer serializer) {
            this.serializer = serializer;
        }

        @GET
        @Path("/stream")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void stream(@Context SseEventSink sink,
                           @Context Sse sse,
                           @HeaderParam(LAST_EVENT_ID_HEADER) String lastEventId) {
            LAST_EVENT_IDS.add(lastEventId);
            CONNECTIONS.countDown();

            long afterId = null == lastEventId ? 0L : Long.parseLong(lastEventId);

            List<OutboundRecord> pending = OUTBOUND.stream()
                    .filter(record -> record.id() > afterId)
                    .limit(2)
                    .toList();

            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .heartbeat(Duration.ofMillis(20))
                    .build()) {
                // Ensure at least one heartbeat comment has time to emit before business events.
                sleepForHeartbeat();

                for (OutboundRecord record : pending) {
                    emitter.send(toEvent(record)).join();
                }
            }
        }

        private SseEvent toEvent(OutboundRecord record) {
            if (record.isPretty()) {
                try {
                    String pretty = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(record.payload());
                    PRETTY_JSON_CONTAINED_LINE_FEEDS.set(pretty.contains("\n"));

                    return SseEvent.builder()
                            .id(String.valueOf(record.id()))
                            .event(record.event())
                            .data(pretty)
                            .retry(Duration.ofMillis(25))
                            .build();
                } catch (JsonProcessingException ex) {
                    throw new IllegalStateException("Failed to serialize pretty JSON payload.", ex);
                }
            }

            if (null != record.payload()) {
                return SseEvents.of(serializer)
                        .id(String.valueOf(record.id()))
                        .event(record.event())
                        .data(record.payload())
                        .retry(Duration.ofMillis(25))
                        .toEvent();
            }

            return SseEvent.builder()
                    .id(String.valueOf(record.id()))
                    .event(record.event())
                    .data(record.data())
                    .retry(Duration.ofMillis(25))
                    .build();
        }

        private static void resetState() {
            LAST_EVENT_IDS.clear();
            CONNECTIONS = new CountDownLatch(2);
            PRETTY_JSON_CONTAINED_LINE_FEEDS.set(false);
        }

        private static boolean awaitAtLeastTwoConnections(Duration timeout) throws InterruptedException {
            return CONNECTIONS.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private static List<String> lastEventIdsSnapshot() {
            return new ArrayList<>(LAST_EVENT_IDS);
        }

        private static boolean prettyJsonContainedLineFeeds() {
            return PRETTY_JSON_CONTAINED_LINE_FEEDS.get();
        }

        private static void sleepForHeartbeat() {
            try {
                Thread.sleep(70L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        private record OutboundRecord(long id,
                                      String event,
                                      String data,
                                      JsonPayload payload,
                                      boolean pretty) {
            private static OutboundRecord text(long id, String event, String data) {
                return new OutboundRecord(id, event, data, null, false);
            }

            private static OutboundRecord payload(long id, String event, JsonPayload payload) {
                return new OutboundRecord(id, event, null, payload, JSON_PRETTY_EVENT.equals(event));
            }

            private boolean isPretty() {
                return pretty;
            }
        }
    }
}



