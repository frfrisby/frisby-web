package software.frisby.web.client.sse;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A single Server-Sent Event as parsed directly off the wire, before dispatch.
 * <p>
 * Mirrors the four fields defined by the SSE wire format: {@code id}, {@code event},
 * {@code data}, and {@code retry}. All fields except {@link #data()} are optional, per
 * the SSE specification. {@link #receivedAt()} is not part of the wire format itself —
 * it is stamped by {@link SseEventParser} the moment the event finishes assembling, so
 * every consumer downstream (including {@link SseMessage#receivedAt()}) reflects the
 * true wire-receipt instant rather than whenever that consumer happened to run.
 * <p>
 * Purely internal — never exposed to a caller. {@link SseEventParser} produces these;
 * {@code DefaultSseListener} consumes {@link #retry()} and {@link #id()} for reconnect
 * bookkeeping (server-suggested reconnect delay, last processed event id for
 * {@code Last-Event-ID} replay) and wraps everything else into a {@link SseMessage} before
 * a registered handler ever sees it.
 *
 * @param id         The event's {@code id} field, if present.
 * @param event      The event's {@code event} field, if present.
 * @param data       The event's raw {@code data} payload; never {@code null}.
 * @param retry      The event's {@code retry} field, if present; must be non-negative when
 *                   present. Per the SSE specification {@code retry} is a non-negative integer
 *                   of milliseconds — a value of zero is legal and means "reconnect
 *                   immediately."
 * @param receivedAt The instant this event finished assembling off the wire; never
 *                   {@code null}.
 */
record RawSseEvent(Optional<String> id,
                    Optional<String> event,
                    String data,
                    Optional<Duration> retry,
                    Instant receivedAt) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @throws software.frisby.core.validation.NullValueException            if {@code id},
     *                                                                       {@code event},
     *                                                                       {@code data},
     *                                                                       {@code retry}, or
     *                                                                       {@code receivedAt}
     *                                                                       is {@code null}.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code retry}
     *                                                                       is present but
     *                                                                       negative.
     */
    RawSseEvent {
        Values.notNull("id", id);
        Values.notNull("event", event);
        Values.notNull("data", data);
        Values.notNull("retry", retry);
        Values.notNull("receivedAt", receivedAt);

        retry.ifPresent(duration -> Durations.notNegative("retry", duration));
    }
}



