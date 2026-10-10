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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.sse.SseEmitter;
import software.frisby.web.server.sse.SseEvent;
import software.frisby.web.test.TestLogging;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real end-to-end loopback stress test targeting a specific suspected race: a resource
 * method that configures a recurring {@link SseEmitter} heartbeat and then, with
 * <strong>no artificial delay whatsoever</strong>, sends real application events on its own
 * thread immediately after {@code build()} returns and repeatedly thereafter — exactly the
 * pattern used by {@code dcws-directory-service}'s {@code CoordinatorSseResource.stream()},
 * the first serious real-world consumer of this library's {@code client-sse}/
 * {@code server-sse} modules.
 * <p>
 * This deliberately omits the {@code sleepForHeartbeat()}-style settling delay present in
 * {@code ClientServerSseCapstoneTest}'s resource ("Ensure at least one heartbeat comment
 * has time to emit before business events") — that delay papers over, rather than
 * verifies the absence of, exactly the race this test is designed to surface: the
 * {@code sse-emitter-heartbeat} thread's recurring ticks (every {@link #HEARTBEAT_MILLIS},
 * starting immediately — see {@code DefaultSseEmitter}'s {@code initialDelay == 0}
 * scheduling) executing concurrently with the resource method's own thread sending a long
 * burst of real events back-to-back, both against the same {@code SseEventSink}.
 * <p>
 * <strong>Single persistent connection, not many reconnects.</strong> An earlier version of
 * this test forced a fresh TCP connection per trial (reconnecting every few milliseconds,
 * hundreds of times). That reliably produced periodic multi-hundred-millisecond-to-low-
 * seconds stalls with no corresponding error or exception anywhere in either the client or
 * server logs. The signature of local ephemeral-port/{@code TIME_WAIT} pressure from
 * opening that many brand-new loopback sockets in rapid succession, not a library defect.
 * Using one long-lived connection and a long in-connection burst isolates the actual
 * heartbeat-vs-send race repeatedly (one trial per event in the burst) without that
 * confounding variable.
 */
@Disabled("Disabled because it is a long-running stress test that is not suitable for regular CI runs. " +
        "Enable and run manually when investigating potential race conditions in SSE handling.")
class ClientServerSseImmediateEventRaceTest {
    private static final String RACE_EVENT = "race-event";
    private static final int EVENT_COUNT = 5000;
    private static final long HEARTBEAT_MILLIS = 7L;

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
                .resources(new ImmediateEventRaceResource())
                .components(TestLogging.forClass(ClientServerSseImmediateEventRaceTest.class))
                .build();

        server.start();

        client = Client.builder()
                .configuration(configuration -> configuration
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
    void sustainedImmediateEventBurstAlongsideHeartbeat_neverLostOrDuplicated()
            throws InterruptedException {
        CountDownLatch delivered = new CountDownLatch(EVENT_COUNT);
        Set<Integer> receivedIds = new ConcurrentSkipListSet<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        try (SseListener listener = SseListener.builder().client(client)
                .path("/immediate-race/stream")
                .parameter("heartbeatMs", String.valueOf(HEARTBEAT_MILLIS))
                .parameter("eventCount", String.valueOf(EVENT_COUNT))
                .onEvent(RACE_EVENT, SseHandler.of(message -> {
                    receivedIds.add(Integer.valueOf(message.id().orElseThrow()));
                    delivered.countDown();
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onError(SseErrorEvent error) {
                        errorCount.incrementAndGet();
                    }
                })
                .build()) {
            listener.connectAsync();

            assertTrue(
                    delivered.await(120, TimeUnit.SECONDS),
                    () -> "Expected " + EVENT_COUNT + " events, only received " + receivedIds.size() + " distinct ids"
            );
        }

        assertEquals(EVENT_COUNT, receivedIds.size(), "Expected no lost or duplicated events across the whole burst");
        assertEquals(
                List.of(1, EVENT_COUNT),
                List.of(receivedIds.iterator().next(), receivedIds.stream().mapToInt(Integer::intValue).max().orElseThrow()),
                "Expected a contiguous id range with no gaps"
        );
        assertEquals(0, errorCount.get());
    }

    @Path("/immediate-race")
    public static final class ImmediateEventRaceResource {
        @GET
        @Path("/stream")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void stream(@Context SseEventSink sink,
                           @Context Sse sse,
                           @QueryParam("heartbeatMs") long heartbeatMs,
                           @QueryParam("eventCount") int eventCount) {
            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .heartbeat(Duration.ofMillis(heartbeatMs))
                    .build()) {
                // Deliberately NOT setting delay anywhere in this loop -- every single
                // send() below is a fresh opportunity for this resource method's own
                // thread to race the independently-scheduled sse-emitter-heartbeat
                // thread, which is ticking every heartbeatMs the entire time this loop
                // runs, concurrently, against the same SseEventSink.
                for (int id = 1; id <= eventCount && emitter.isOpen(); id++) {
                    emitter.send(SseEvent.builder()
                            .id(String.valueOf(id))
                            .event(RACE_EVENT)
                            .data("payload-" + id)
                            .build()).join();
                }
            }
        }
    }
}

