package software.frisby.web.server.sse;

import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.Optional;

record DefaultSseEvent(
        Optional<String> id,
        Optional<String> event,
        String data,
        Optional<Duration> retry) implements SseEvent {
    DefaultSseEvent {
        Values.notNull("id", id);
        Values.notNull("event", event);
        Values.notNull("data", data);
        Values.notNull("retry", retry);
    }
}

