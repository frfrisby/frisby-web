package software.frisby.web.server;

import software.frisby.web.server.event.RequestCompletedEvent;
import software.frisby.web.server.event.ServerEventListener;

/**
 * A {@link ServerEventListener} that discards all events.
 * <p>
 * Used as the default listener when the caller does not register one via
 * {@link ServerBuilder#eventListener(ServerEventListener)}.  All callback methods
 * are empty and incur no observable overhead.
 */
final class NoOpServerEventListener implements ServerEventListener {
    static final NoOpServerEventListener INSTANCE = new NoOpServerEventListener();

    private NoOpServerEventListener() {
    }

    @Override
    public void onRequestCompleted(RequestCompletedEvent event) {
        // No-op — events are intentionally discarded.
    }
}
