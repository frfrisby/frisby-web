package software.frisby.web.client.security.oauth2;

import java.time.Duration;

/**
 * No-op implementation of {@link TokenEventListener} used when the caller has not
 * registered a listener.
 */
final class NoOpTokenEventListener implements TokenEventListener {
    static final NoOpTokenEventListener INSTANCE = new NoOpTokenEventListener();

    private NoOpTokenEventListener() {
    }

    @Override
    public void onTokenFetched(Duration latency) {
    }

    @Override
    public void onTokenFetchFailed(Duration latency, Throwable cause) {
    }
}

