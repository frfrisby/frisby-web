package software.frisby.web.server;

import software.frisby.core.validation.Sequences;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;
import software.frisby.web.server.event.ServerEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class DefaultServerBuilder implements ServerBuilder {
    private static final String CONFIGURATION = "configuration";
    private static final String RESOURCES = "resources";
    private static final String COMPONENTS = "components";
    private static final String AUTHENTICATION = "authentication";
    private static final String EVENT_LISTENER = "eventListener";
    private static final String HEALTH_CHECK_PATH = "healthCheck.path";
    private static final String DEFAULT_HEALTH_CHECK_PATH = "/health";

    // Matches a valid health check path: must start with '/', consist of one or more
    // path segments containing only letters, digits, hyphens, underscores, and dots,
    // separated by single slashes. Rejects trailing slashes, consecutive slashes,
    // whitespace, and URI-special characters such as '#' and '?'.
    // The consecutive-slash and whitespace constraints fall naturally from the character
    // whitelist -- no lookahead is needed.
    private static final Pattern VALID_PATH_PATTERN =
            Pattern.compile("^/[a-zA-Z0-9\\-._]+(/[a-zA-Z0-9\\-._]+)*$");

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
        this.configuration = Values.notNull(CONFIGURATION, configuration);
        return this;
    }

    @Override
    public ServerBuilder resources(Object... resources) {
        return this.resources(List.of(Sequences.notEmpty(RESOURCES, resources)));
    }

    @Override
    public ServerBuilder resources(List<Object> resources) {
        this.resources.addAll(Sequences.notEmpty(RESOURCES, resources));
        return this;
    }

    @Override
    public ServerBuilder components(Object... components) {
        return this.components(List.of(Sequences.notEmpty(COMPONENTS, components)));
    }

    @Override
    public ServerBuilder components(List<Object> components) {
        this.components.addAll(Sequences.notEmpty(COMPONENTS, components));
        return this;
    }

    @Override
    public ServerBuilder eventListener(ServerEventListener eventListener) {
        this.eventListener = Values.notNull(EVENT_LISTENER, eventListener);
        return this;
    }

    @Override
    public ServerBuilder authentication(AuthenticationProvider... providers) {
        return this.authentication(List.of(Sequences.notEmpty(AUTHENTICATION, providers)));
    }

    @Override
    public ServerBuilder authentication(List<AuthenticationProvider> providers) {
        this.authenticationProviders.addAll(Sequences.notEmpty(AUTHENTICATION, providers));
        return this;
    }

    @Override
    public ServerBuilder healthCheck() {
        this.healthCheckPath = DEFAULT_HEALTH_CHECK_PATH;
        return this;
    }

    @Override
    public ServerBuilder healthCheck(String path) {
        this.healthCheckPath = Strings.notBlankWithMatches(HEALTH_CHECK_PATH, path, VALID_PATH_PATTERN);
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

