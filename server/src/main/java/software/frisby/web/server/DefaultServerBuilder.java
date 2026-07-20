package software.frisby.web.server;

import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;
import software.frisby.web.server.event.ServerEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class DefaultServerBuilder implements ServerBuilder {
    private static final String CONFIGURATION_ARGUMENT_NAME = "configuration";
    private static final String RESOURCES_ARGUMENT_NAME = "resources";
    private static final String COMPONENTS_ARGUMENT_NAME = "components";
    private static final String AUTHENTICATION_PROVIDERS_ARGUMENT_NAME = "authentication";
    private static final String EVENT_LISTENER_ARGUMENT_NAME = "eventListener";
    private static final String HEALTH_CHECK_PATH_ARGUMENT_NAME = "healthCheck.path";
    private static final String DEFAULT_HEALTH_CHECK_PATH = "/health";

    // Maximum character length enforced before the regex runs — prevents ReDoS on
    // pathological inputs and is a reasonable upper bound for any real health check path.
    private static final int MAX_PATH_LENGTH = 256;

    // Matches a valid health check path: must start with '/', consist of one or more
    // path segments containing only letters, digits, hyphens, underscores, and dots,
    // separated by single slashes. Rejects trailing slashes, consecutive slashes,
    // whitespace, and URI-special characters such as '#' and '?'.
    // Quantifiers are bounded ({1,128} per segment, {1,64} segment groups) to prevent
    // catastrophic backtracking on adversarial inputs.
    private static final Pattern VALID_PATH_PATTERN =
            Pattern.compile("^/[a-zA-Z0-9\\-._]{1,128}(/[a-zA-Z0-9\\-._]{1,128}){0,64}$");

    private final List<Object> resources;
    private final List<Object> components;
    private final List<AuthenticationProvider> authenticationProviders;
    private ServerConfiguration configuration;
    private ServerEventListener eventListener;
    private String healthCheckPath;

    DefaultServerBuilder() {
        this.configuration = null;
        this.resources = new ArrayList<>();
        this.components = new ArrayList<>();
        this.authenticationProviders = new ArrayList<>();
        this.eventListener = NoOpServerEventListener.INSTANCE;
        this.healthCheckPath = null;
    }

    @Override
    public ServerBuilder configuration(ServerConfiguration configuration) {
        this.configuration = Values.notNull(CONFIGURATION_ARGUMENT_NAME, configuration);
        return this;
    }

    @Override
    public ServerBuilder resources(Object... resources) {
        return this.resources(List.of(Sequences.notEmpty(RESOURCES_ARGUMENT_NAME, resources)));
    }

    @Override
    public ServerBuilder resources(List<Object> resources) {
        this.resources.addAll(Sequences.notEmpty(RESOURCES_ARGUMENT_NAME, resources));
        return this;
    }

    @Override
    public ServerBuilder components(Object... components) {
        return this.components(List.of(Sequences.notEmpty(COMPONENTS_ARGUMENT_NAME, components)));
    }

    @Override
    public ServerBuilder components(List<Object> components) {
        this.components.addAll(Sequences.notEmpty(COMPONENTS_ARGUMENT_NAME, components));
        return this;
    }

    @Override
    public ServerBuilder eventListener(ServerEventListener eventListener) {
        this.eventListener = Values.notNull(EVENT_LISTENER_ARGUMENT_NAME, eventListener);
        return this;
    }

    @Override
    public ServerBuilder authentication(AuthenticationProvider... providers) {
        return this.authentication(List.of(Sequences.notEmpty(AUTHENTICATION_PROVIDERS_ARGUMENT_NAME, providers)));
    }

    @Override
    public ServerBuilder authentication(List<AuthenticationProvider> providers) {
        this.authenticationProviders.addAll(Sequences.notEmpty(AUTHENTICATION_PROVIDERS_ARGUMENT_NAME, providers));
        return this;
    }

    @Override
    public ServerBuilder healthCheck() {
        this.healthCheckPath = DEFAULT_HEALTH_CHECK_PATH;
        return this;
    }

    @Override
    public ServerBuilder healthCheck(String path) {
        this.healthCheckPath = Strings.notBlankWithMaxLengthAndMatches(HEALTH_CHECK_PATH_ARGUMENT_NAME, path, MAX_PATH_LENGTH, VALID_PATH_PATTERN);
        return this;
    }

    @Override
    public Server build() {
        if (null == configuration) {
            throw new IllegalStateException(
                    "The 'configuration' value is invalid.  A ServerConfiguration is required."
            );
        }

        if (resources.isEmpty()) {
            throw new IllegalStateException(
                    "The 'resources' value is invalid.  At least one JAX-RS resource must be registered."
            );
        }

        return new DefaultServer(
                configuration,
                List.copyOf(resources),
                List.copyOf(components),
                List.copyOf(authenticationProviders),
                eventListener,
                healthCheckPath
        );
    }
}

