package software.frisby.web.client;

import software.frisby.web.client.event.ClientEventListener;
import software.frisby.web.client.event.RequestCompletedEvent;
import software.frisby.web.client.event.RequestFailedEvent;

/**
 * A {@link ClientEventListener} that discards all events.
 * <p>
 * Used as the default listener when the caller does not register one via
 * {@link ClientBuilder#eventListener(ClientEventListener)}.  All callback methods
 * are empty and incur no observable overhead.
 */
final class NoOpClientEventListener implements ClientEventListener {
    static final NoOpClientEventListener INSTANCE = new NoOpClientEventListener();

    private NoOpClientEventListener() {
    }

    @Override
    public void onRequestCompleted(RequestCompletedEvent event) {
        // No-op — events are intentionally discarded.
    }

    @Override
    public void onRequestFailed(RequestFailedEvent event) {
        // No-op — events are intentionally discarded.
    }
}

