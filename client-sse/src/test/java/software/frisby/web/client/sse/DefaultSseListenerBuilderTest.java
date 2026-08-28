package software.frisby.web.client.sse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.DuplicateElementsException;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.NumericValueOutsideRangeException;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Chunk 7 unit tests — {@link DefaultSseListenerBuilder} validation.
 * <p>
 * Uses a {@link Client} pointed at a placeholder URI; no network call is ever made by
 * these tests since {@code SseListenerBuilder} methods are pure configuration until
 * {@link SseListener#connectAsync()} is invoked.
 */
class DefaultSseListenerBuilderTest {
    private static Client client;

    @BeforeAll
    static void createClient() {
        client = Client.builder()
                .configuration(c -> c
                        .uri(URI.create("http://localhost"))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();
    }

    @Nested
    class Builder {
        @Test
        void nullClient_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> SseListener.builder().client(null));
        }

        @Test
        void validClient_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client));
        }

        @Test
        void clientNeverCalled_buildThrowsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> SseListener.builder().path("/sse/stream").build()
            );
        }

        @Test
        void clientCalledAfterNavigation_stillValidatesNavigation() {
            assertThrows(
                    BlankValueException.class,
                    () -> SseListener.builder().path(" ").client(client)
            );
        }
    }

    @Nested
    class Path {
        @Test
        void nullPath_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> SseListener.builder().client(client).path((String) null));
        }

        @Test
        void blankPath_throwsBlankValueException() {
            assertThrows(BlankValueException.class, () -> SseListener.builder().client(client).path(" "));
        }

        @Test
        void validPath_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client).path("/sse/stream"));
        }
    }

    @Nested
    class OnEvent {
        @Test
        void blankEvent_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> SseListener.builder().client(client)
                            .onEvent(" ", SseHandler.of(String.class, message -> {
                            }))
            );
        }

        @Test
        void nullHandler_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> SseListener.builder().client(client).onEvent("event", (SseHandler) null)
            );
        }

        @Test
        void zeroConcurrency_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> SseHandler.of(String.class, (Consumer<SseMessage<String>>) message -> {
                    }).concurrency(0)
            );
        }

        @Test
        void negativeConcurrency_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> SseHandler.of(String.class, (Consumer<SseMessage<String>>) message -> {
                    }).concurrency(-1)
            );
        }

        @Test
        void validRegistration_returnsBuilder() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .onEvent("event", SseHandler.of(String.class, message -> {
                            }))
            );
        }

        @Test
        void validRawRegistration_returnsBuilder() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .onEvent("event", SseHandler.of(message -> {
                            }))
            );
        }

        @Test
        void sameEventRegisteredTwiceViaOnEvent_throwsDuplicateElementsException() {
            assertThrows(
                    DuplicateElementsException.class,
                    () -> SseListener.builder().client(client)
                            .onEvent("file-ready", SseHandler.of(String.class, message -> {
                            }))
                            .onEvent("file-ready", SseHandler.of(String.class, message -> {
                            }))
            );
        }

        @Test
        void eventRegisteredViaOnEventThenOnEventBatch_throwsDuplicateElementsException() {
            assertThrows(
                    DuplicateElementsException.class,
                    () -> SseListener.builder().client(client)
                            .onEvent("file-ready", SseHandler.of(String.class, message -> {
                            }))
                            .onEvent("file-ready", SseBatchHandler.of(String.class, messages -> {
                            }))
            );
        }

        @Test
        void eventRegisteredViaOnEventBatchThenOnEvent_throwsDuplicateElementsException() {
            assertThrows(
                    DuplicateElementsException.class,
                    () -> SseListener.builder().client(client)
                            .onEvent("file-ready", SseBatchHandler.of(String.class, messages -> {
                            }))
                            .onEvent("file-ready", SseHandler.of(String.class, message -> {
                            }))
            );
        }

        @Test
        void sameEventRegisteredTwiceViaOnEventBatch_throwsDuplicateElementsException() {
            assertThrows(
                    DuplicateElementsException.class,
                    () -> SseListener.builder().client(client)
                            .onEvent("file-ready", SseBatchHandler.of(String.class, messages -> {
                            }))
                            .onEvent("file-ready", SseBatchHandler.of(String.class, messages -> {
                            }))
            );
        }
    }

    @Nested
    class Build {
        @Test
        void minimalConfiguration_buildsListenerThatIsNotYetOpen() {
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .onEvent("message", SseHandler.of(message -> {
                    }))
                    .build();

            assertNotNull(listener);
            assertFalse(listener.isOpen());
        }

        @Test
        void build_startsNoThreadsAndPerformsNoIo() {
            int threadCountBefore = Thread.activeCount();

            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .onEvent("message", SseHandler.of(message -> {
                    }))
                    .build();

            assertEquals(threadCountBefore, Thread.activeCount());
            assertFalse(listener.isOpen());
        }

        @Test
        void noHandlerRegistered_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> SseListener.builder().client(client)
                            .path("/sse/stream")
                            .build()
            );
        }

        @Test
        void onlyOnUnhandledEventRegistered_buildsListener() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .path("/sse/stream")
                            .onUnhandledEvent(message -> {
                            })
                            .build()
            );
        }

        @Test
        void onlyOnUnhandledEventBatchRegistered_buildsListener() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .path("/sse/stream")
                            .onUnhandledEvent(SseBatchHandler.of(messages -> {
                            }))
                            .build()
            );
        }

        @Test
        void onlyOnEventBatchRegistered_buildsListener() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .path("/sse/stream")
                            .onEvent("message", SseBatchHandler.of(messages -> {
                            }))
                            .build()
            );
        }
    }

    @Nested
    class OnUnhandledEvent {
        @Test
        void nullBatchHandler_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> SseListener.builder().client(client).onUnhandledEvent((SseBatchHandler) null)
            );
        }

        @Test
        void typedBatchHandler_throwsIllegalArgumentException() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SseListener.builder().client(client)
                            .onUnhandledEvent(SseBatchHandler.of(String.class, messages -> {
                            }))
            );
        }

        @Test
        void validRawBatchHandler_returnsBuilder() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .onUnhandledEvent(SseBatchHandler.of(messages -> {
                            }))
            );
        }
    }
}


