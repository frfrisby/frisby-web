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
 *   <li>{@code GET /sse/stream?includeEventField=false} — omits the {@code event:} line entirely
 *       for every generated event, rather than writing {@code event: message} — used to
 *       distinguish "the producer never set an event type" from "the producer explicitly chose
 *       the name message" in client-side dispatch tests</li>
 *   <li>{@code GET /sse/stream?channel={name}} — isolates the in-memory event log used for
 *       {@code Last-Event-ID} replay so concurrent tests do not interfere with one another;
 *       defaults to {@value #DEFAULT_CHANNEL}</li>
 *   <li>{@code GET /sse/stream?retryMs={n}} — emits a {@code retry:} field (milliseconds) on
 *       the first generated event only, for exercising a client's server-supplied reconnect
 *       delay handling</li>
 *   <li>{@code GET /sse/stream?payload=object} — emits {@code data} as a JSON object,
 *       {@code {"value":"event-N"}}, instead of the bare string {@code "event-N"} — for
 *       exercising typed {@code Class<T>} deserialization</li>
 *   <li>{@code GET /sse/stream?payload=array} — emits {@code data} as a single-element JSON
 *       array, {@code ["event-N"]}, instead of the bare string — for exercising generically-typed
 *       ({@code GenericType<List<T>>}) deserialization</li>
 *   <li>{@code GET /sse/stream?malformedEventId={n}} — overrides the {@code data} field of the
 *       event whose {@code id} equals {@code n} with a deliberately invalid JSON fragment,
 *       regardless of {@code payload} — for exercising a single item's deserialization failure
 *       without invalidating an entire batch</li>
 *   <li>{@code GET /sse/stream?alternateEventTypes=true} — alternates each generated event's
 *       {@code event} field between {@code "type-a"} (odd {@code id}) and {@code "type-b"}
 *       (even {@code id}), overriding {@code includeEventField} — for exercising two different
 *       {@code onEvent} registrations on one connection</li>
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
    private static final String DEFAULT_PAYLOAD = "plain";
    private static final Map<String, List<StoredEvent>> EVENT_LOGS = new ConcurrentHashMap<>();

    /**
     * Writes a configurable sequence of SSE events, optionally replaying only those events
     * whose {@code id} is greater than the supplied {@code Last-Event-ID} header value.
     *
     * @param count              The number of events to generate for this channel the first
     *                           time it is requested; ignored on subsequent requests for the
     *                           same channel.
     * @param heartbeat          When {@code true}, a {@code : keep-alive} comment line precedes
     *                           every event.
     * @param closeAfterMs       When present, the number of milliseconds to sleep immediately
     *                           before writing the final event.
     * @param includeEventField  When {@code false}, generated events omit the {@code event:}
     *                           line entirely instead of writing {@code event: message}.
     *                           Ignored when {@code alternateEventTypes} is {@code true}.
     * @param channel            Isolates the in-memory event log used for {@code Last-Event-ID}
     *                           replay.
     * @param retryMs            When present, the first generated event carries a {@code retry:}
     *                           field with this millisecond value; ignored on subsequent requests
     *                           for the same channel (the event log is generated only once).
     * @param payload            {@code "plain"} (default) for a bare {@code "event-N"} string,
     *                           {@code "object"} for a JSON object ({@code {"value":"event-N"}}),
     *                           or {@code "array"} for a single-element JSON array
     *                           ({@code ["event-N"]}).
     * @param malformedEventId   When present, overrides the {@code data} field of the event with
     *                           this {@code id} with a deliberately invalid JSON fragment,
     *                           regardless of {@code payload}.
     * @param alternateEventTypes When {@code true}, alternates each event's {@code event} field
     *                           between {@code "type-a"} (odd {@code id}) and {@code "type-b"}
     *                           (even {@code id}), overriding {@code includeEventField}.
     * @param lastEventId        The incoming {@code Last-Event-ID} header value, if any.
     * @return A streaming {@code text/event-stream} response.
     */
    @GET
    @Path("/stream")
    public Response stream(@QueryParam("count") @DefaultValue("" + DEFAULT_EVENT_COUNT) int count,
                            @QueryParam("heartbeat") @DefaultValue("false") boolean heartbeat,
                            @QueryParam("closeAfterMs") Long closeAfterMs,
                            @QueryParam("includeEventField") @DefaultValue("true") boolean includeEventField,
                            @QueryParam("channel") @DefaultValue(DEFAULT_CHANNEL) String channel,
                            @QueryParam("retryMs") Long retryMs,
                            @QueryParam("payload") @DefaultValue(DEFAULT_PAYLOAD) String payload,
                            @QueryParam("malformedEventId") Long malformedEventId,
                            @QueryParam("alternateEventTypes") @DefaultValue("false") boolean alternateEventTypes,
                            @HeaderParam(LAST_EVENT_ID) String lastEventId) {
        List<StoredEvent> events = EVENT_LOGS.computeIfAbsent(
                channel,
                key -> generateEvents(
                        count,
                        includeEventField,
                        retryMs,
                        payload,
                        malformedEventId,
                        alternateEventTypes
                )
        );

        long afterId = null == lastEventId ? 0L : Long.parseLong(lastEventId);

        List<StoredEvent> toSend = events.stream()
                .filter(event -> event.id() > afterId)
                .toList();

        StreamingOutput output = out -> writeEvents(out, toSend, heartbeat, closeAfterMs);

        return Response.ok(output).type(TEXT_EVENT_STREAM).build();
    }

    private static List<StoredEvent> generateEvents(int count,
                                                     boolean includeEventField,
                                                     Long retryMs,
                                                     String payload,
                                                     Long malformedEventId,
                                                     boolean alternateEventTypes) {
        List<StoredEvent> events = new ArrayList<>();

        for (int id = 1; id <= count; id++) {
            String event = resolveEventField(id, includeEventField, alternateEventTypes);
            Long retry = 1 == id ? retryMs : null;
            String data = null != malformedEventId && malformedEventId == id
                    ? "{not-valid-json"
                    : toPayload(payload, id);
            events.add(new StoredEvent(id, event, data, retry));
        }

        return List.copyOf(events);
    }

    private static String resolveEventField(int id, boolean includeEventField, boolean alternateEventTypes) {
        if (alternateEventTypes) {
            return 1 == id % 2 ? "type-a" : "type-b";
        }

        return includeEventField ? "message" : null;
    }

    private static String toPayload(String payload, int id) {
        String value = "event-" + id;

        if ("object".equals(payload)) {
            return "{\"value\":\"" + value + "\"}";
        }

        if ("array".equals(payload)) {
            return "[\"" + value + "\"]";
        }

        return value;
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

    private record StoredEvent(long id, String event, String data, Long retryMs) {
        String toWireFormat() {
            String eventLine = null == event ? "" : "event: " + event + "\n";
            String retryLine = null == retryMs ? "" : "retry: " + retryMs + "\n";
            return "id: " + id + "\n" + eventLine + retryLine + "data: " + data + "\n\n";
        }
    }
}

