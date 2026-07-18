package software.frisby.web.test.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A compact JUL {@link Formatter} for test console output.
 *
 * <p>Translates raw JUL level names to their {@link System.Logger.Level} equivalents so that
 * the console output matches the level vocabulary used throughout the test suite:
 *
 * <pre>
 *   SEVERE  → ERROR
 *   WARNING → WARNING
 *   INFO    → INFO
 *   FINE    → DEBUG
 *   FINER, FINEST, and all lower values → TRACE
 * </pre>
 *
 * <p>Records from logger names that begin with {@code "java."}, {@code "javax."}, {@code "sun."},
 * {@code "jdk."}, or {@code "org.junit."} are suppressed — they originate inside the JVM or test
 * framework and are not relevant to test output.
 *
 * <p>Output format:
 * <pre>
 *   HH:mm:ss.SSS [LEVEL  ] logger.name - message
 *   optional stack trace
 * </pre>
 *
 * <p>Reference this class in {@code logging.properties} inside any module's
 * {@code src/test/resources}:
 * <pre>
 *   java.util.logging.ConsoleHandler.formatter = software.frisby.web.test.log.TestLogFormatter
 * </pre>
 */
public final class TestLogFormatter extends Formatter {
    private static final String[] SUPPRESSED_PREFIXES = {
            "java.", "javax.", "sun.", "jdk.", "org.junit."
    };

    private static boolean isSuppressed(String loggerName) {
        if (null == loggerName) {
            return false;
        }

        for (String prefix : SUPPRESSED_PREFIXES) {
            if (loggerName.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    private static String toSystemLevel(java.util.logging.Level julLevel) {
        if (null == julLevel) {
            return "UNKNOWN";
        }

        int value = julLevel.intValue();

        if (value >= java.util.logging.Level.SEVERE.intValue()) {
            return "ERROR";
        }

        if (value >= java.util.logging.Level.WARNING.intValue()) {
            return "WARNING";
        }

        if (value >= java.util.logging.Level.INFO.intValue()) {
            return "INFO";
        }

        if (value >= java.util.logging.Level.FINE.intValue()) {
            return "DEBUG";
        }

        return "TRACE";
    }

    private static String formatThrown(LogRecord record) {
        Throwable thrown = record.getThrown();

        if (null == thrown) {
            return "";
        }

        StringWriter sw = new StringWriter();
        sw.append(System.lineSeparator());
        thrown.printStackTrace(new PrintWriter(sw));

        return sw.toString();
    }

    /**
     * Formats a {@link LogRecord} for test console output.
     *
     * @param record The log record to format.
     * @return The formatted log line, or an empty string for suppressed records.
     */
    @Override
    public String format(LogRecord record) {
        if (isSuppressed(record.getLoggerName())) {
            return "";
        }

        ZonedDateTime time = ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault());
        String level = toSystemLevel(record.getLevel());
        String message = formatMessage(record);
        String thrown = formatThrown(record);

        return String.format(
                "%1$tH:%1$tM:%1$tS.%1$tL [%2$-7s] %3$s - %4$s%5$s%n",
                time,
                level,
                record.getLoggerName(),
                message,
                thrown
        );
    }
}

