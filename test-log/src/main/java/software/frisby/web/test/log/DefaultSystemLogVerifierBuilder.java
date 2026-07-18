package software.frisby.web.test.log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class DefaultSystemLogVerifierBuilder implements SystemLogVerifierBuilder {
    private final List<LogExpectation> expectations;
    private final List<LoggerLevelConfig> levelConfigs;

    DefaultSystemLogVerifierBuilder() {
        this.expectations = new ArrayList<>();
        this.levelConfigs = new ArrayList<>();
    }

    @Override
    public SystemLogVerifierBuilder configure(Class<?> clazz, System.Logger.Level level) {
        this.levelConfigs.add(new LoggerLevelConfig(clazz.getName(), level));
        return this;
    }

    @Override
    public SystemLogVerifierBuilder configure(String loggerName, System.Logger.Level level) {
        this.levelConfigs.add(new LoggerLevelConfig(loggerName, level));
        return this;
    }

    @Override
    public SystemLogVerifierBuilder expect(LogExpectation... expectations) {
        this.expectations.addAll(Arrays.asList(expectations));
        return this;
    }

    @Override
    public SystemLogVerifier build() {
        DefaultSystemLogVerifier verifier = new DefaultSystemLogVerifier(
                this.expectations,
                this.levelConfigs
        );

        HandlerManager.register(verifier);
        return verifier;
    }
}

