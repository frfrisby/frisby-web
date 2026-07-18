package software.frisby.web.server;

import org.junit.jupiter.api.Test;
import software.frisby.web.server.event.RequestCompletedEvent;

import java.time.Duration;

class NoOpServerEventListenerTest {
    @Test
    void onRequestCompleted_doesNotThrow() {
        RequestCompletedEvent event = new RequestCompletedEvent(
                "GET",
                "/ping",
                200,
                Duration.ofMillis(5),
                0L,
                32L
        );

        NoOpServerEventListener.INSTANCE.onRequestCompleted(event);
    }
}
