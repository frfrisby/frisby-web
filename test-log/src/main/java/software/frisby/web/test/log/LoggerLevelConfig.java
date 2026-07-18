package software.frisby.web.test.log;

import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

/**
 * Holds a logger name paired with the {@link System.Logger.Level} it should be set to for the
 * duration of a single test.  Used by {@link DefaultSystemLogVerifier} to temporarily override
 * the effective log level of a specific logger and restore it on {@link SystemLogVerifier#close()}.
 */
final class LoggerLevelConfig {
    /** The fully-qualified name of the logger to configure. */
    private final String loggerName;

    /** The level to apply for the duration of the test. */
    private final System.Logger.Level level;

    LoggerLevelConfig(String loggerName, System.Logger.Level level) {
        this.loggerName = Strings.notBlank("loggerName", loggerName);
        this.level = Values.notNull("level", level);
    }

    String loggerName() {
        return loggerName;
    }

    System.Logger.Level level() {
        return level;
    }
}

