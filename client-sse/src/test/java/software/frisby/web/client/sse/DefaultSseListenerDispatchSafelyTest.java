package software.frisby.web.client.sse;

import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test for {@link DefaultSseListener#dispatchSafely}'s {@code null == delivery} guard.
 * <p>
 * That branch is structurally unreachable through the real {@code Buffer → Transform →
 * Action} pipeline — verified directly against {@code frisby-core:concurrency}'s own
 * {@code TargetManager.postToTarget} source, which never forwards a {@code null} item to a
 * linked target at all. A failed deserialization (see {@link DefaultSseListener#dispatchSafely})
 * returning {@code null} therefore stops that item before it can ever reach this method with a
 * null {@code delivery} argument via a live connection.
 * <p>
 * {@code dispatchSafely} is deliberately package-private (not {@code private}) so this guard
 * can still be exercised directly, in isolation — matching the same rationale already
 * established for {@link DefaultSseListener#throwIfFatal} and
 * {@link DefaultSseListener#closeStreamSafely}.
 */
class DefaultSseListenerDispatchSafelyTest {
    @Test
    void nullDelivery_isANoOp_callbackNeverInvoked() {
        Client client = Client.builder()
                .configuration(c -> c
                        .uri(URI.create("http://localhost"))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();

        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        SseHandler handler = SseHandler.of(message -> callbackInvoked.set(true));

        DefaultSseListener listener = (DefaultSseListener) SseListener.builder().client(client)
                .path("/sse/stream")
                .onEvent("message", handler)
                .build();

        assertDoesNotThrow(() -> listener.dispatchSafely(handler, null));
        assertFalse(callbackInvoked.get(), "Expected a null delivery to never invoke the handler's callback");
    }
}

