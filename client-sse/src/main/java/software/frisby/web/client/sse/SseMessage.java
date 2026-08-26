package software.frisby.web.client.sse;

import java.time.Instant;
import java.util.Optional;

/**
 * The value delivered to every registered handler on an {@code SseListener} — typed or
 * raw alike.
 * <p>
 * This is the single dispatch contract for the module: typed handlers (registered via
 * {@code onEvent(String, Class, Consumer)} or the {@code GenericType} overload) receive an
 * {@code SseMessage<T>} whose {@link #body()} is the deserialized payload; raw handlers
 * receive an {@code SseMessage<String>} whose {@link #body()} is the untouched wire-format
 * {@code data} string. Either way, {@link #id()}, {@link #event()}, and
 * {@link #receivedAt()} are always available alongside the payload.
 *
 * @param <T> The body type — a deserialized payload type for typed handlers, or
 *            {@link String} for raw handlers.
 * @see SseListenerBuilder
 */
public interface SseMessage<T> {
    /**
     * Returns the event's {@code id} field, if present.
     * <p>
     * When present, this is the value the connection sends back as the
     * {@code Last-Event-ID} header on reconnect once this message has been processed.
     *
     * @return The event id, or {@link Optional#empty()} if the event had no {@code id} field.
     */
    Optional<String> id();

    /**
     * Returns the event type this message was dispatched under.
     * <p>
     * Resolved at dispatch time — an event with no {@code event} field on the wire is
     * resolved to {@code "message"}, matching the default used to look up a registered
     * handler.
     *
     * @return The resolved event type; never {@code null} or blank.
     */
    String event();

    /**
     * Returns the message body.
     * <p>
     * For a typed handler, this is the payload deserialized from the wire-format
     * {@code data} string via the client's configured {@code JsonSerializer}. For a raw
     * handler, this is the untouched wire-format {@code data} string itself
     * ({@code SseMessage<String>}).
     *
     * @return The message body; never {@code null}.
     */
    T body();

    /**
     * Returns the instant this message's underlying event was received and fully parsed
     * off the wire by the connection's reader thread.
     * <p>
     * Captured at parse time, not at dispatch time — so this value is unaffected by any
     * buffering, batching, or backpressure delay between receipt and handler invocation.
     *
     * @return The receipt timestamp; never {@code null}.
     */
    Instant receivedAt();
}


