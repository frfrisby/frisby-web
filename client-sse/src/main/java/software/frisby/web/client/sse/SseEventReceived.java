package software.frisby.web.client.sse;

import software.frisby.core.validation.Values;

import java.time.Instant;
import java.util.Optional;

/**
 * The value delivered to {@link SseListenerObserver#onEventReceived(SseEventReceived)} the
 * moment a raw event is accepted into a dispatch pipeline — a real-time, per-event
 * companion to {@link SseListener#pipelineStats()}'s on-demand snapshot.
 * <p>
 * Mirrors {@link SseMessage} minus {@link SseMessage#body()} — this fires before
 * deserialization, so no typed or raw body is available yet, only wire-level identity.
 * <p>
 * Fired only when the event is actually accepted into a pipeline. An event rejected under
 * {@link BufferFullPolicy#DROP} or one that triggers {@link BufferFullPolicy#DISCONNECT}
 * never reaches this callback — those are already reported via
 * {@link SseListenerObserver#onDropped(SseMessage)} and
 * {@link SseListenerObserver#onReconnect(SseReconnectEvent)} respectively, and are
 * deliberately not double-reported here.
 *
 * @param event        The event's raw wire {@code event} field, if present — same
 *                     name/semantics as {@link SseMessage#event()}.
 * @param registeredAs The handler event type this event was actually dispatched under, if
 *                     any — present when a named {@code onEvent} handler matched;
 *                     {@link Optional#empty()} when this event was routed to the catch-all
 *                     unhandled-event pipeline, regardless of whether {@link #event()}
 *                     itself was present. See {@link SseListenerObserver} for the three
 *                     resulting combinations.
 * @param id           The event's {@code id} field, if present.
 * @param receivedAt   The instant this event finished assembling off the wire.
 * @see SseEventProcessed
 * @see SseListenerObserver
 */
public record SseEventReceived(Optional<String> event,
                               Optional<String> registeredAs,
                               Optional<String> id,
                               Instant receivedAt) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @param event        the raw wire event field; must not be {@code null}
     * @param registeredAs the handler event type; must not be {@code null}
     * @param id           the event id; must not be {@code null}
     * @param receivedAt   the instant this event finished assembling; must not be {@code null}
     * @throws software.frisby.core.validation.NullValueException if any field is {@code null}.
     */
    public SseEventReceived {
        Values.notNull("event", event);
        Values.notNull("registeredAs", registeredAs);
        Values.notNull("id", id);
        Values.notNull("receivedAt", receivedAt);
    }
}

