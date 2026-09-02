package software.frisby.web.server.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.client.Headers;
import software.frisby.web.client.SseSpec;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSseTest {
    private static final String HEARTBEAT_COMMENT = "keep-alive";
    private static final String MESSAGE_EVENT_NAME = "message";

    private static Server server;
    private static Client client;

    @BeforeAll
    static void startServer() {
        JsonSerializer serializer = new TestJsonSerializer();

        server = Server.builder()
                .configuration(configuration -> configuration
                        .port(0)
                        .host("localhost")
                        .serializer(serializer)
                )
                .resources(new ServerSseTestResource())
                .build();

        server.start();

        client = Client.builder()
                .configuration(configuration -> configuration
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
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
    void singleEvent_received() throws IOException {
        ParsedStream parsed = readStream(spec -> spec.path("/server-sse/single"));

        assertEquals(1, parsed.events().size());
        assertEquals("1", parsed.events().get(0).id());
        assertEquals(MESSAGE_EVENT_NAME, parsed.events().get(0).event());
        assertEquals("event-1", parsed.events().get(0).data());
        assertTrue(parsed.eofReached());
    }

    @Test
    void multipleEvents_receivedInSequence() throws IOException {
        ParsedStream parsed = readStream(spec -> spec.path("/server-sse/multiple"));

        assertEquals(3, parsed.events().size());
        assertEquals("event-1", parsed.events().get(0).data());
        assertEquals("event-2", parsed.events().get(1).data());
        assertEquals("event-3", parsed.events().get(2).data());
        assertTrue(parsed.eofReached());
    }

    @Test
    void heartbeatComments_areIgnoredByParser() throws IOException {
        ParsedStream parsed = readStream(spec -> spec.path("/server-sse/heartbeat"));

        assertTrue(parsed.heartbeatComments() > 0);
        assertEquals(1, parsed.events().size());
        assertEquals("event-with-heartbeat", parsed.events().get(0).data());
    }

    @Test
    void streamEnds_cleanEofObservedByReader() throws IOException {
        ParsedStream parsed = readStream(spec -> spec.path("/server-sse/multiple"));

        assertTrue(parsed.eofReached());
        assertFalse(parsed.events().isEmpty());
    }

    @Test
    void lastEventIdHeader_roundTripsToResource() throws IOException {
        ParsedStream parsed = readStream(spec -> spec.path("/server-sse/last-event-id")
                .header(Headers.LAST_EVENT_ID, "42"));

        assertEquals(1, parsed.events().size());
        assertEquals("42", parsed.events().get(0).data());
    }

    @Test
    void emitterLifecycle_logsInfoAndTrace() throws IOException {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .configure(DefaultSseEmitter.class, System.Logger.Level.TRACE)
                .expect(LogExpectation.builder()
                        .logger(DefaultSseEmitter.class)
                        .level(System.Logger.Level.INFO)
                        .predicate(event -> event.message().contains("SSE stream opened."))
                        .build()
                )
                .expect(LogExpectation.builder()
                        .logger(DefaultSseEmitter.class)
                        .level(System.Logger.Level.TRACE)
                        .predicate(event -> event.message().contains("SSE event sent."))
                        .build()
                )
                .expect(LogExpectation.builder()
                        .logger(DefaultSseEmitter.class)
                        .level(System.Logger.Level.INFO)
                        .predicate(event -> event.message().contains("SSE stream closed."))
                        .build()
                )
                .build()) {
            ParsedStream parsed = readStream(spec -> spec.path("/server-sse/single"));

            assertEquals(1, parsed.events().size());
            verifier.assertExpectations(Duration.ofSeconds(2));
        }
    }

    private ParsedStream readStream(java.util.function.Consumer<SseSpec> configuration) throws IOException {
        SseSpec spec = client.sse();
        configuration.accept(spec);

        InputStream stream = spec.stream().body();

        try (stream; BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<ParsedEvent> events = new ArrayList<>();
            List<String> dataLines = new ArrayList<>();
            int heartbeatComments = 0;
            String id = null;
            String event = null;
            Long retryMs = null;
            String line;

            while (null != (line = reader.readLine())) {
                if (line.startsWith(":")) {
                    if (line.contains(HEARTBEAT_COMMENT)) {
                        heartbeatComments++;
                    }

                    continue;
                }

                if (line.isEmpty()) {
                    if (null != id || null != event || null != retryMs || !dataLines.isEmpty()) {
                        events.add(new ParsedEvent(id, event, String.join("\n", dataLines), retryMs));
                    }

                    id = null;
                    event = null;
                    retryMs = null;
                    dataLines.clear();
                    continue;
                }

                if (line.startsWith("id:")) {
                    id = stripPrefix(line, "id:");
                    continue;
                }

                if (line.startsWith("event:")) {
                    event = stripPrefix(line, "event:");
                    continue;
                }

                if (line.startsWith("retry:")) {
                    retryMs = Long.parseLong(stripPrefix(line, "retry:"));
                    continue;
                }

                if (line.startsWith("data:")) {
                    dataLines.add(stripPrefix(line, "data:"));
                }
            }

            return new ParsedStream(List.copyOf(events), heartbeatComments, true);
        }
    }

    private static String stripPrefix(String line, String prefix) {
        String value = line.substring(prefix.length());

        if (value.startsWith(" ")) {
            return value.substring(1);
        }

        return value;
    }

    private record ParsedEvent(String id, String event, String data, Long retryMs) {
    }

    private record ParsedStream(List<ParsedEvent> events, int heartbeatComments, boolean eofReached) {
    }

    @Path("/server-sse")
    public static final class ServerSseTestResource {
        @GET
        @Path("/single")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void single(@Context SseEventSink sink, @Context Sse sse) {
            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .build()) {
                emitter.send(
                        SseEvent.builder()
                                .id("1")
                                .event(MESSAGE_EVENT_NAME)
                                .data("event-1")
                                .build()
                ).join();
            }
        }

        @GET
        @Path("/multiple")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void multiple(@Context SseEventSink sink, @Context Sse sse) {
            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .build()) {
                for (int i = 1; i <= 3; i++) {
                    emitter.send(
                            SseEvent.builder()
                                    .id(String.valueOf(i))
                                    .event(MESSAGE_EVENT_NAME)
                                    .data("event-" + i)
                                    .build()
                    ).join();
                }
            }
        }

        @GET
        @Path("/heartbeat")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void heartbeat(@Context SseEventSink sink, @Context Sse sse) {
            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .heartbeat(Duration.ofMillis(20))
                    .build()) {
                sleep(90);

                emitter.send(
                        SseEvent.builder()
                                .id("1")
                                .event(MESSAGE_EVENT_NAME)
                                .data("event-with-heartbeat")
                                .build()
                ).join();
            }
        }

        @GET
        @Path("/last-event-id")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void lastEventId(@Context SseEventSink sink,
                                @Context Sse sse,
                                @HeaderParam("Last-Event-ID") String lastEventId) {
            String value = null == lastEventId ? "<none>" : lastEventId;

            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .build()) {
                emitter.send(
                        SseEvent.builder()
                                .id("43")
                                .event(MESSAGE_EVENT_NAME)
                                .data(value)
                                .build()
                ).join();
            }
        }

        @GET
        @Path("/delayed")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void delayed(@Context SseEventSink sink,
                            @Context Sse sse,
                            @QueryParam("delayMs") long delayMs) {
            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .build()) {
                sleep(delayMs);

                emitter.send(
                        SseEvent.builder()
                                .id("1")
                                .event(MESSAGE_EVENT_NAME)
                                .data("delayed")
                                .build()
                ).join();
            }
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class TestJsonSerializer implements JsonSerializer {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        @Override
        public byte[] serialize(Object value) {
            try {
                return OBJECT_MAPPER.writeValueAsBytes(value);
            } catch (JsonProcessingException ex) {
                throw new IllegalArgumentException("Serialization failed.", ex);
            }
        }

        @Override
        public <T> T deserialize(byte[] content, Class<T> type) {
            try {
                return OBJECT_MAPPER.readValue(content, type);
            } catch (IOException ex) {
                throw new IllegalArgumentException("Deserialization failed.", ex);
            }
        }

        @Override
        public <T> T deserialize(byte[] content, GenericType<T> genericType) {
            try {
                return OBJECT_MAPPER.readValue(
                        content,
                        OBJECT_MAPPER.getTypeFactory().constructType(genericType.type())
                );
            } catch (IOException ex) {
                throw new IllegalArgumentException("Deserialization failed.", ex);
            }
        }
    }
}


