package software.frisby.web.server.sse;

import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.function.Supplier;

record DefaultScheduledEvent(Duration interval, Supplier<SseEvent> supplier) implements ScheduledEvent {
    DefaultScheduledEvent {
        Values.notNull("interval", interval);
        Values.notNull("supplier", supplier);
    }
}

