package software.frisby.web.client;

import software.frisby.core.validation.Values;
import software.frisby.web.client.security.SecurityProvider;

/**
 * Package-private implementation of {@link Client}.
 */
final class DefaultClient implements Client {
    private final HttpEngine engine;
    private final SecurityProvider defaultSecurity;

    DefaultClient(HttpEngine engine, SecurityProvider defaultSecurity) {
        this.engine = Values.notNull("engine", engine);
        this.defaultSecurity = defaultSecurity;
    }

    @Override
    public GetSpec get() {
        return new GetRequest(engine, defaultSecurity);
    }

    @Override
    public PostSpec post() {
        return new PostRequest(engine, defaultSecurity);
    }

    @Override
    public PutSpec put() {
        return new PutRequest(engine, defaultSecurity);
    }

    @Override
    public PatchSpec patch() {
        return new PatchRequest(engine, defaultSecurity);
    }

    @Override
    public DeleteSpec delete() {
        return new DeleteRequest(engine, defaultSecurity);
    }

    @Override
    public HeadSpec head() {
        return new HeadRequest(engine, defaultSecurity);
    }

    @Override
    public ClientConfiguration configuration() {
        return engine.configuration();
    }
}

