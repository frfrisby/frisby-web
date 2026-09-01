package software.frisby.web.client.sse;

import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for {@link DefaultSseListener#dispatchBatchSafely}'s {@code null == deliveries}
 * guard.
 * <p>
 * That specific half of the guard is structurally unreachable through the real
 * {@code Batch → Transform → Action} pipeline — {@code DefaultSseListener.deserializeBatchSafely}
 * always returns a list, never {@code null}. The {@code deliveries.isEmpty()} half, in
 * contrast, {@code is} reachable (a batch whose every item fails to deserialize) and is
 * exercised via a real connection in
 * {@code ClientSseTypedDispatchTest.entireBatchMalformed_deliversNothingForThatBatch_butSubsequentBatchesStillArrive}.
 * This test covers only the {@code null} half directly, the same way
 * {@link DefaultSseListenerDispatchSafelyTest} covers {@code dispatchSafely}'s analogous
 * unreachable {@code null} guard.
 */
class DefaultSseListenerDispatchBatchSafelyTest {
    @Test
    void nullDeliveries_isANoOp_callbackNeverInvoked() {
        Client client = Client.builder()
                .configuration(c -> c
                        .uri(URI.create("http://localhost"))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();

        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        SseBatchHandler handler = SseBatchHandler.of(messages -> callbackInvoked.set(true));

        DefaultSseListener listener = (DefaultSseListener) SseListener.builder().client(client)
                .path("/sse/stream")
                .onEvent("message", handler)
                .build();

        assertDoesNotThrow(() -> listener.dispatchBatchSafely(handler, null));
        assertFalse(callbackInvoked.get(), "Expected null deliveries to never invoke the handler's callback");
    }

    @Test
    void emptyDeliveries_isANoOp_callbackNeverInvoked() {
        Client client = Client.builder()
                .configuration(c -> c
                        .uri(URI.create("http://localhost"))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();

        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        SseBatchHandler handler = SseBatchHandler.of(messages -> callbackInvoked.set(true));

        DefaultSseListener listener = (DefaultSseListener) SseListener.builder().client(client)
                .path("/sse/stream")
                .onEvent("message", handler)
                .build();

        assertDoesNotThrow(() -> listener.dispatchBatchSafely(handler, List.of()));
        assertFalse(callbackInvoked.get(), "Expected empty deliveries to never invoke the handler's callback");
    }
}

