package software.frisby.web.client.sse;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.time.Instant;
import java.util.Optional;

/**
 * Default implementation of {@link SseMessage}.
 *
 * @param id         The event's {@code id} field, if present.
 * @param event      The resolved event type; must not be blank.
 * @param body       The message body; must not be {@code null}.
 * @param receivedAt The receipt timestamp; must not be {@code null}.
 * @param <T>        The body type.
 */
record DefaultSseMessage<T>(Optional<String> id,
                             String event,
                             T body,
                             Instant receivedAt) implements SseMessage<T> {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @throws software.frisby.core.validation.NullValueException  if {@code id}, {@code event},
     *                                                             {@code body}, or
     *                                                             {@code receivedAt} is
     *                                                             {@code null}.
     * @throws software.frisby.core.validation.BlankValueException if {@code event} is blank.
     */
    DefaultSseMessage {
        Values.notNull("id", id);
        Strings.notBlank("event", event);
        Values.notNull("body", body);
        Values.notNull("receivedAt", receivedAt);
    }
}

