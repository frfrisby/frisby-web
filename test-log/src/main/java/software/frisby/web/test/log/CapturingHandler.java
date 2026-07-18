package software.frisby.web.test.log;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * A JUL {@link Handler} that converts every published {@link LogRecord} into a
 * {@link SystemLogEvent} and forwards it to the currently active {@link DefaultSystemLogVerifier}
 * via {@link HandlerManager}.
 * <p>
 * Installed once on the root JUL logger at {@link java.util.logging.Level#ALL} when
 * {@link HandlerManager} is first loaded.  Never removed for the lifetime of the test JVM.
 */
final class CapturingHandler extends Handler {
    CapturingHandler() {
        setLevel(java.util.logging.Level.ALL);
    }

    private static String formatMessage(LogRecord record) {
        String format = record.getMessage();

        if (null == format) {
            return "";
        }

        Object[] params = record.getParameters();

        if (null == params || params.length == 0) {
            return format;
        }

        try {
            return MessageFormat.format(format, params);
        } catch (Exception ex) {
            return format;
        }
    }

    /**
     * Maps a JUL {@link java.util.logging.Level} to the nearest {@link System.Logger.Level}.
     * Returns {@code null} for JUL levels that have no meaningful System.Logger equivalent
     * (e.g. {@link java.util.logging.Level#OFF}).
     */
    private static System.Logger.Level toSystemLevel(java.util.logging.Level julLevel) {
        if (null == julLevel) {
            return null;
        }

        int value = julLevel.intValue();

        if (value >= java.util.logging.Level.SEVERE.intValue()) {
            return System.Logger.Level.ERROR;
        }

        if (value >= java.util.logging.Level.WARNING.intValue()) {
            return System.Logger.Level.WARNING;
        }

        if (value >= java.util.logging.Level.INFO.intValue()) {
            return System.Logger.Level.INFO;
        }

        if (value >= java.util.logging.Level.FINE.intValue()) {
            return System.Logger.Level.DEBUG;
        }

        if (value > java.util.logging.Level.ALL.intValue()) {
            return System.Logger.Level.TRACE;
        }

        return null;
    }

    @Override
    public void publish(LogRecord record) {
        if (null == record) {
            return;
        }

        System.Logger.Level level = toSystemLevel(record.getLevel());

        if (null == level) {
            return;
        }

        String message = formatMessage(record);
        Instant when = record.getInstant();

        HandlerManager.accept(
                new SystemLogEvent(
                        record.getLoggerName(),
                        level,
                        message,
                        record.getThrown(),
                        when
                )
        );
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}

