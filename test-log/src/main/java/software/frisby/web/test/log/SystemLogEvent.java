package software.frisby.web.test.log;

import java.time.Instant;

/**
 * Represents a single log event captured from a {@link System.Logger} during a test run.
 *
 * @see SystemLogVerifier
 * @see LogExpectation
 */
public final class SystemLogEvent {
    /** The fully-qualified name of the logger that recorded this event. */
    private final String loggerName;

    /** The severity level at which this event was recorded. */
    private final System.Logger.Level level;

    /** The formatted log message. */
    private final String message;

    /** The throwable associated with this event, or {@code null} if none was provided. */
    private final Throwable thrown;

    /** The instant at which this event was recorded. */
    private final Instant when;

    SystemLogEvent(String loggerName,
                   System.Logger.Level level,
                   String message,
                   Throwable thrown,
                   Instant when) {
        this.loggerName = loggerName;
        this.level = level;
        this.message = message;
        this.thrown = thrown;
        this.when = when;
    }

    /**
     * Returns the fully-qualified name of the logger that recorded this event.
     *
     * @return The logger name.
     */
    public String loggerName() {
        return loggerName;
    }

    /**
     * Returns the severity level at which this event was recorded.
     *
     * @return The {@link System.Logger.Level} value.
     */
    public System.Logger.Level level() {
        return level;
    }

    /**
     * Returns the formatted log message.
     *
     * @return The message string.
     */
    public String message() {
        return message;
    }

    /**
     * Returns the throwable associated with this event, or {@code null} if none was provided.
     *
     * @return The throwable, or {@code null}.
     */
    public Throwable thrown() {
        return thrown;
    }

    /**
     * Returns the instant at which this event was recorded.
     *
     * @return The {@link Instant} value.
     */
    public Instant when() {
        return when;
    }

    @Override
    public String toString() {
        return String.format(
                "SystemLogEvent{loggerName='%s', level=%s, message='%s'}",
                loggerName,
                level,
                message
        );
    }
}

