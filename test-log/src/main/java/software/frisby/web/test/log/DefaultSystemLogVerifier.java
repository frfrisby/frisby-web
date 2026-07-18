package software.frisby.web.test.log;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

final class DefaultSystemLogVerifier implements SystemLogVerifier {
    private static final String COLOR_BRIGHT_YELLOW = "\033[0;93m";
    private static final String COLOR_RESET = "\033[0m";

    private final Map<ExpectationKey, List<LogExpectation>> expectationMap;
    private final Map<String, java.util.logging.Level> savedLevels;
    private final AtomicInteger errorCounter;
    private final AtomicInteger warningCounter;
    private final AtomicInteger infoCounter;
    private final AtomicInteger debugCounter;
    private final AtomicInteger traceCounter;
    private final Instant createdWhen;

    DefaultSystemLogVerifier(List<LogExpectation> expectations, List<LoggerLevelConfig> levelConfigs) {
        this.expectationMap = new HashMap<>();

        for (LogExpectation expectation : expectations) {
            ExpectationKey key = new ExpectationKey(expectation.loggerName(), expectation.level());
            this.expectationMap.putIfAbsent(key, new ArrayList<>());
            this.expectationMap.get(key).add(expectation);
        }

        this.savedLevels = new HashMap<>();

        for (LoggerLevelConfig config : levelConfigs) {
            Logger julLogger = Logger.getLogger(config.loggerName());
            this.savedLevels.put(config.loggerName(), julLogger.getLevel());
            julLogger.setLevel(toJulLevel(config.level()));
        }

        this.errorCounter = new AtomicInteger(0);
        this.warningCounter = new AtomicInteger(0);
        this.infoCounter = new AtomicInteger(0);
        this.debugCounter = new AtomicInteger(0);
        this.traceCounter = new AtomicInteger(0);

        // Truncate to millisecond resolution to match LogRecord instant precision and avoid
        // discarding messages written in the same millisecond the verifier was created.
        this.createdWhen = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static java.util.logging.Level toJulLevel(System.Logger.Level level) {
        return switch (level) {
            case ALL -> java.util.logging.Level.ALL;
            case TRACE -> java.util.logging.Level.FINER;
            case DEBUG -> java.util.logging.Level.FINE;
            case INFO -> java.util.logging.Level.INFO;
            case WARNING -> java.util.logging.Level.WARNING;
            case ERROR -> java.util.logging.Level.SEVERE;
            case OFF -> java.util.logging.Level.OFF;
        };
    }

    private static String asBrightYellow(String input) {
        return COLOR_BRIGHT_YELLOW + input + COLOR_RESET;
    }

    void accept(SystemLogEvent event) {
        if (event.when().compareTo(this.createdWhen) < 0) {
            return;
        }

        switch (event.level()) {
            case ERROR -> this.errorCounter.incrementAndGet();
            case WARNING -> this.warningCounter.incrementAndGet();
            case INFO -> this.infoCounter.incrementAndGet();
            case DEBUG -> this.debugCounter.incrementAndGet();
            case TRACE -> this.traceCounter.incrementAndGet();
            default -> {
            }
        }

        if (this.expectationMap.isEmpty()) {
            return;
        }

        ExpectationKey key = new ExpectationKey(event.loggerName(), event.level());
        List<LogExpectation> matching = this.expectationMap.get(key);

        if (null == matching) {
            return;
        }

        for (LogExpectation expectation : matching) {
            expectation.test(event);
        }
    }

    @Override
    public int errorCount() {
        return this.errorCounter.get();
    }

    @Override
    public int warningCount() {
        return this.warningCounter.get();
    }

    @Override
    public int infoCount() {
        return this.infoCounter.get();
    }

    @Override
    public int debugCount() {
        return this.debugCounter.get();
    }

    @Override
    public int traceCount() {
        return this.traceCounter.get();
    }

    @Override
    public void assertExpectations() {
        this.assertExpectations(Duration.ZERO);
    }

    @Override
    public void assertExpectations(Duration timeout) {
        for (Map.Entry<ExpectationKey, List<LogExpectation>> entry : this.expectationMap.entrySet()) {
            for (LogExpectation expectation : entry.getValue()) {
                expectation.await(timeout);
            }
        }
    }

    @Override
    public void close() {
        HandlerManager.reset();

        for (Map.Entry<String, java.util.logging.Level> entry : this.savedLevels.entrySet()) {
            Logger.getLogger(entry.getKey()).setLevel(entry.getValue());
        }

        int error = errorCount();
        int warning = warningCount();
        int info = infoCount();
        int debug = debugCount();
        int trace = traceCount();
        int total = error + warning + info + debug + trace;

        System.out.printf(
                "SystemLogVerifier closed.  Messages captured — %s%n",
                asBrightYellow(
                        String.format(
                                "Total: %d, Error: %d, Warning: %d, Info: %d, Debug: %d, Trace: %d",
                                total,
                                error,
                                warning,
                                info,
                                debug,
                                trace
                        )
                )
        );
    }
}

