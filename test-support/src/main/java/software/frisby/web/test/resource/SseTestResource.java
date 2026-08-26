package software.frisby.web.test.resource;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-written {@code text/event-stream} JAX-RS resource used to unblock {@code client} /
 * {@code client-sse} integration testing before the {@code server-sse} module exists.
 * <p>
 * This is intentionally primitive — it writes the SSE wire format directly to the response
 * {@link OutputStream} rather than depending on {@code jersey-media-sse}, and it does not model
 * or preview any {@code server-sse} type.
 *
 * <ul>
 *   <li>{@code GET /sse/stream} — writes {@code count} events (default {@value #DEFAULT_EVENT_COUNT}),
 *       each with {@code id}, {@code event}, and {@code data} fields, flushing after each, then
 *       closes the stream normally</li>
 *   <li>{@code GET /sse/stream?heartbeat=true} — interleaves a {@code : keep-alive} comment line
 *       before each event</li>
 *   <li>{@code GET /sse/stream?closeAfterMs={n}} — sleeps for {@code n} milliseconds immediately
 *       before writing the final event, holding the connection open for that duration</li>
 *   <li>{@code GET /sse/stream?channel={name}} — isolates the in-memory event log used for
 *       {@code Last-Event-ID} replay so concurrent tests do not interfere with one another;
 *       defaults to {@value #DEFAULT_CHANNEL}</li>
 *   <li>A {@code Last-Event-ID} request header causes only events with an {@code id} greater
 *       than the supplied value to be replayed, using a simple in-memory event log keyed by
 *       {@code channel}</li>
 * </ul>
 */
@Path("/sse")
public final class SseTestResource {
    private static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String LAST_EVENT_ID = "Last-Event-ID";
    private static final String KEEP_ALIVE_COMMENT = ": keep-alive\n\n";
    private static final int DEFAULT_EVENT_COUNT = 3;
    private static final String DEFAULT_CHANNEL = "default";
    private static final Map<String, List<StoredEvent>> EVENT_LOGS = new ConcurrentHashMap<>();

    /**
     * Writes a configurable sequence of SSE events, optionally replaying only those events
     * whose {@code id} is greater than the supplied {@code Last-Event-ID} header value.
     *
     * @param count        The number of events to generate for this channel the first time it
     *                     is requested; ignored on subsequent requests for the same channel.
     * @param heartbeat    When {@code true}, a {@code : keep-alive} comment line precedes every
     *                     event.
     * @param closeAfterMs When present, the number of milliseconds to sleep immediately before
     *                     writing the final event.
     * @param channel      Isolates the in-memory event log used for {@code Last-Event-ID} replay.
     * @param lastEventId  The incoming {@code Last-Event-ID} header value, if any.
     * @return A streaming {@code text/event-stream} response.
     */
    @GET
    @Path("/stream")
    public Response stream(@QueryParam("count") @DefaultValue("" + DEFAULT_EVENT_COUNT) int count,
                            @QueryParam("heartbeat") @DefaultValue("false") boolean heartbeat,
                            @QueryParam("closeAfterMs") Long closeAfterMs,
                            @QueryParam("channel") @DefaultValue(DEFAULT_CHANNEL) String channel,
                            @HeaderParam(LAST_EVENT_ID) String lastEventId) {
        List<StoredEvent> events = EVENT_LOGS.computeIfAbsent(channel, key -> generateEvents(count));

        long afterId = null == lastEventId ? 0L : Long.parseLong(lastEventId);

        List<StoredEvent> toSend = events.stream()
                .filter(event -> event.id() > afterId)
                .toList();

        StreamingOutput output = out -> writeEvents(out, toSend, heartbeat, closeAfterMs);

        return Response.ok(output).type(TEXT_EVENT_STREAM).build();
    }

    private static List<StoredEvent> generateEvents(int count) {
        List<StoredEvent> events = new ArrayList<>();

        for (int id = 1; id <= count; id++) {
            events.add(new StoredEvent(id, "message", "event-" + id));
        }

        return List.copyOf(events);
    }

    private static void writeEvents(OutputStream out,
                                     List<StoredEvent> events,
                                     boolean heartbeat,
                                     Long closeAfterMs) throws IOException {
        for (int i = 0; i < events.size(); i++) {
            if (heartbeat) {
                out.write(KEEP_ALIVE_COMMENT.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            boolean isLast = i == events.size() - 1;

            if (isLast && null != closeAfterMs) {
                sleep(closeAfterMs);
            }

            out.write(events.get(i).toWireFormat().getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record StoredEvent(long id, String event, String data) {
        String toWireFormat() {
            return "id: " + id + "\nevent: " + event + "\ndata: " + data + "\n\n";
        }
    }
}

