package software.frisby.web.client.sse;

import jakarta.ws.rs.GET;
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
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.sse.SseEmitter;
import software.frisby.web.test.TestLogging;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end regression test for the reconnect-storm investigation
 * (see {@code temp/frisby-web-sse-reconnect-storm-investigation.md}): a real
 * {@link SseEmitter}-backed stream, configured with a heartbeat interval <strong>exactly
 * equal to</strong> the client's {@code readTimeout} — the precise configuration that
 * produced an unbroken reconnect loop roughly every heartbeat interval in the
 * {@code dcws-directory-service} consumer, because the first heartbeat did not fire until a
 * full interval had already elapsed. The corresponding unit-level regression test for the
 * scheduling fix itself is
 * {@code DefaultSseEmitterTest.Lifecycle#heartbeatEnabled_firstHeartbeatFiresWellBeforeFullInterval}
 * in the {@code server-sse} module.
 * <p>
 * This test never sends a single application event — the connection survives (or doesn't)
 * purely on heartbeat traffic, isolating the exact failure mode from the investigation
 * without any other variable.
 */
class ClientServerSseHeartbeatStormTest {
    private static final long HEARTBEAT_MILLIS = 300L;
    private static final long HOLD_MILLIS = 3_000L;

    private static Server server;
    private static Client client;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(configuration -> configuration
                        .port(0)
                        .host("localhost")
                        .serializer(JacksonSerializer.builder().build())
                )
                .resources(new HeartbeatOnlyResource())
                .components(TestLogging.forClass(ClientServerSseHeartbeatStormTest.class))
                .build();

        server.start();

        // readTimeout deliberately equals HEARTBEAT_MILLIS -- this is the exact
        // configuration that raced the emitter's first heartbeat before the initialDelay
        // fix in DefaultSseEmitter.
        client = Client.builder()
                .configuration(configuration -> configuration
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofMillis(HEARTBEAT_MILLIS))
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
    void heartbeatIntervalEqualsReadTimeout_noHeartbeatOnlyEvents_neverReconnects()
            throws InterruptedException {
        List<SseReconnectEvent> reconnects = new CopyOnWriteArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        SseListener listener = SseListener.builder().client(client)
                .path("/heartbeat-only/stream")
                .parameter("heartbeatMs", String.valueOf(HEARTBEAT_MILLIS))
                .parameter("holdMs", String.valueOf(HOLD_MILLIS))
                .onUnhandledEvent(message -> {
                })
                .observer(new SseListenerObserver() {
                    @Override
                    public void onReconnect(SseReconnectEvent event) {
                        reconnects.add(event);
                    }

                    @Override
                    public void onError(SseErrorEvent error) {
                        errorCount.incrementAndGet();
                    }
                })
                .build();

        try (listener) {
            listener.connectAsync();

            // Hold well past several heartbeat intervals, but comfortably inside the
            // server's HOLD_MILLIS so the connection is still open (not a clean
            // end-of-stream) when we assert.
            Thread.sleep(HOLD_MILLIS - 500L);

            assertEquals(
                    List.of(),
                    reconnects,
                    "Expected zero reconnects while the server heartbeat keeps the connection alive"
            );
            assertEquals(0, errorCount.get());
        }
    }

    @Path("/heartbeat-only")
    public static final class HeartbeatOnlyResource {
        @GET
        @Path("/stream")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void stream(@Context SseEventSink sink,
                           @Context Sse sse,
                           @QueryParam("heartbeatMs") long heartbeatMs,
                           @QueryParam("holdMs") long holdMs) {
            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .heartbeat(Duration.ofMillis(heartbeatMs))
                    .build()) {
                long deadline = System.currentTimeMillis() + holdMs;

                while (emitter.isOpen() && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}


