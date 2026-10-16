package software.frisby.web.server.sse;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.function.Supplier;

final class DefaultScheduledEventBuilder implements ScheduledEventBuilder {
    private static final String INTERVAL_ARGUMENT_NAME = "interval";
    private static final String SUPPLIER_ARGUMENT_NAME = "supplier";

    private Duration interval;
    private Supplier<SseEvent> supplier;

    DefaultScheduledEventBuilder() {
        this.interval = null;
        this.supplier = null;
    }

    @Override
    public ScheduledEventBuilder interval(Duration interval) {
        this.interval = Durations.positive(
                INTERVAL_ARGUMENT_NAME,
                interval
        );

        return this;
    }

    @Override
    public ScheduledEventBuilder supplier(Supplier<SseEvent> supplier) {
        this.supplier = Values.notNull(
                SUPPLIER_ARGUMENT_NAME,
                supplier
        );

        return this;
    }

    @Override
    public ScheduledEvent build() {
        Values.notNull(
                INTERVAL_ARGUMENT_NAME,
                interval
        );

        Values.notNull(
                SUPPLIER_ARGUMENT_NAME,
                supplier
        );

        return new DefaultScheduledEvent(
                interval,
                supplier
        );
    }
}

