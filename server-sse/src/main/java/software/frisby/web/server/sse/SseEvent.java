package software.frisby.web.server.sse;

import java.time.Duration;
import java.util.Optional;

/**
 * An outbound Server-Sent Event emitted by a server endpoint.
 * <p>
 * Mirrors the {@code text/event-stream} wire fields:
 * <ul>
 *     <li>{@link #id()} is optional.</li>
 *     <li>{@link #event()} is optional.</li>
 *     <li>{@link #data()} is required.</li>
 *     <li>{@link #retry()} is optional.</li>
 * </ul>
 */
public interface SseEvent {
    /**
     * Creates a new builder for an outbound SSE event.
     *
     * @return A new {@link SseEventBuilder}.
     */
    static SseEventBuilder builder() {
        return new DefaultSseEventBuilder();
    }

    /**
     * Returns the optional event id field.
     *
     * @return The event id, if set.
     */
    Optional<String> id();

    /**
     * Returns the optional event type field.
     *
     * @return The event type, if set.
     */
    Optional<String> event();

    /**
     * Returns the required event data field.
     *
     * @return The non-null data payload.
     */
    String data();

    /**
     * Returns the optional retry hint field.
     *
     * @return The reconnect delay hint, if set.
     */
    Optional<Duration> retry();
}
