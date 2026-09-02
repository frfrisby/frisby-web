package software.frisby.web.server.sse;

import software.frisby.core.validation.Durations;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

final class DefaultSseEventBuilder implements SseEventBuilder {
    private static final String ID_ARGUMENT_NAME = "id";
    private static final String EVENT_ARGUMENT_NAME = "event";
    private static final String DATA_ARGUMENT_NAME = "data";
    private static final String RETRY_ARGUMENT_NAME = "retry";

    private static final int MAX_EVENT_ID_LENGTH = 512;

    private static final Pattern EVENT_PATTERN = Pattern.compile("^[^\\r\\n]+$");
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^[^\\u0000]+$");

    private String id;
    private String event;
    private String data;
    private Duration retry;

    DefaultSseEventBuilder() {
        this.id = null;
        this.event = null;
        this.data = null;
        this.retry = null;
    }

    @Override
    public SseEventBuilder id(String id) {
        this.id = Strings.notBlankWithMaxLengthAndMatches(
                ID_ARGUMENT_NAME,
                id,
                MAX_EVENT_ID_LENGTH,
                EVENT_ID_PATTERN
        );

        return this;
    }

    @Override
    public SseEventBuilder event(String event) {
        this.event = Strings.notBlankWithMatches(
                EVENT_ARGUMENT_NAME,
                event,
                EVENT_PATTERN
        );

        return this;
    }

    @Override
    public SseEventBuilder data(String data) {
        this.data = Values.notNull(
                DATA_ARGUMENT_NAME,
                data
        );

        return this;
    }

    @Override
    public SseEventBuilder retry(Duration retry) {
        this.retry = Durations.notNegative(
                RETRY_ARGUMENT_NAME,
                retry
        );

        return this;
    }

    @Override
    public SseEvent build() {
        Values.notNull(
                DATA_ARGUMENT_NAME,
                data
        );

        return new DefaultSseEvent(
                Optional.ofNullable(id),
                Optional.ofNullable(event),
                data,
                Optional.ofNullable(retry)
        );
    }
}
