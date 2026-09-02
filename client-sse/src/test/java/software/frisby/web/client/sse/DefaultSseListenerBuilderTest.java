package software.frisby.web.client.sse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.DuplicateElementsException;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.NumericValueOutsideRangeException;
import software.frisby.web.client.Client;
import software.frisby.web.client.PathParameter;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.jackson.JacksonSerializer;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

        @Test
        void clientCalledAfterValidNavigation_appliesQueuedOperationsWithoutThrowing() {
            // clientCalledAfterNavigation_stillValidatesNavigation above only ever queues
            // one operation that immediately throws once client(...) replays it, so the
            // replay loop's own normal-completion path (looping back to check for a next
            // queued operation, then exiting normally with no exception) was never
            // exercised by any existing test until this one.
            assertNotNull(SseListener.builder().path("/sse/stream").client(client));
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

        @Test
        void validPathWithSingleNamedParameter_returnsBuilder() {
            assertNotNull(
                    SseListener.builder().client(client).path("/sse/stream/{channel}", "channel", "orders")
            );
        }

        @Test
        void validPathWithMultipleNamedParameters_returnsBuilder() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .path(
                                    "/sse/{channel}/stream/{clientId}",
                                    PathParameter.of("channel", "orders"),
                                    PathParameter.of("clientId", "abc123")
                            )
            );
        }
    }

    @Nested
    class Parameter {
        @Test
        void validSingleValue_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client).parameter("channel", "orders"));
        }

        @Test
        void validMultipleValues_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client).parameter("tag", "a", "b"));
        }
    }

    @Nested
    class Header {
        @Test
        void validSingleValue_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client).header("X-Custom", "value"));
        }

        @Test
        void validMultipleValues_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client).header("X-Custom", "a", "b"));
        }
    }

    @Nested
    class Cookie {
        @Test
        void validCookie_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client).cookie(new HttpCookie("session", "abc123")));
        }
    }

    @Nested
    class LastEventId {
        @Test
        void nullId_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> SseListener.builder().client(client).lastEventId(null));
        }

        @Test
        void blankId_throwsBlankValueException() {
            assertThrows(BlankValueException.class, () -> SseListener.builder().client(client).lastEventId(" "));
        }

        @Test
        void validId_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client).lastEventId("42"));
        }
    }

    @Nested
    class Executor {
        @Test
        void nullExecutor_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> SseListener.builder().client(client).executor(null));
        }

        @Test
        void validExecutor_returnsBuilder() {
            ExecutorService executor = Executors.newSingleThreadExecutor();

            try {
                assertNotNull(SseListener.builder().client(client).executor(executor));
            } finally {
                executor.shutdown();
            }
        }
    }

    @Nested
    class CloseTimeout {
        @Test
        void nullTimeout_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> SseListener.builder().client(client).closeTimeout(null));
        }

        @Test
        void zeroTimeout_throwsDurationOutsideRangeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> SseListener.builder().client(client).closeTimeout(Duration.ZERO)
            );
        }

        @Test
        void negativeTimeout_throwsDurationOutsideRangeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> SseListener.builder().client(client).closeTimeout(Duration.ofSeconds(-1))
            );
        }

        @Test
        void validTimeout_returnsBuilder() {
            assertNotNull(SseListener.builder().client(client).closeTimeout(Duration.ofSeconds(5)));
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
        void batchHandlerZeroCapacity_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> SseBatchHandler.of(String.class, (Consumer<List<SseMessage<String>>>) messages -> {
                    }).capacity(0)
            );
        }

        @Test
        void batchHandlerNegativeCapacity_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> SseBatchHandler.of(String.class, (Consumer<List<SseMessage<String>>>) messages -> {
                    }).capacity(-1)
            );
        }

        @Test
        void validRegistrationWithBatchHandlerCapacityTuning_returnsBuilder() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .onEvent("event", SseBatchHandler.of(String.class, messages -> {
                            }).capacity(2048))
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
        void closeCalledWithoutEverConnecting_isANoOpAndDoesNotThrow() {
            // connectAsync() is what creates the reader thread and dispatch pipelines —
            // never calling it means close() must handle every one of its internal
            // null-guarded fields (readerThread, handlerPipelines, unhandledPipeline,
            // ownedExecutor) all still being null, rather than assuming connectAsync()
            // was always called first.
            SseListener listener = SseListener.builder().client(client)
                    .path("/sse/stream")
                    .onEvent("message", SseHandler.of(message -> {
                    }))
                    .build();

            assertDoesNotThrow(listener::close);
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
        void nullHandler_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> SseListener.builder().client(client).onUnhandledEvent((SseHandler) null)
            );
        }

        @Test
        void typedHandler_throwsIllegalArgumentException() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SseListener.builder().client(client)
                            .onUnhandledEvent(SseHandler.of(String.class, message -> {
                            }))
            );
        }

        @Test
        void genericallyTypedHandler_throwsIllegalArgumentException() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SseListener.builder().client(client)
                            .onUnhandledEvent(SseHandler.of(new GenericType<List<String>>() {
                            }, message -> {
                            }))
            );
        }

        @Test
        void validRawHandler_returnsBuilder() {
            assertNotNull(
                    SseListener.builder().client(client)
                            .onUnhandledEvent(SseHandler.of(message -> {
                            }))
            );
        }

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
        void genericallyTypedBatchHandler_throwsIllegalArgumentException() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SseListener.builder().client(client)
                            .onUnhandledEvent(SseBatchHandler.of(new GenericType<List<String>>() {
                            }, messages -> {
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


