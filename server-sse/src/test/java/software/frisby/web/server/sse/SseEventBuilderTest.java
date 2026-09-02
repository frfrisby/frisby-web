package software.frisby.web.server.sse;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.PatternMismatchException;
import software.frisby.core.validation.StringLengthOutsideRangeException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseEventBuilderTest {
    private static final String NULL_DATA_MESSAGE = "The 'data' value is invalid. The value must not be null.";

    @Nested
    class Id {
        @Test
        void nullId_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> SseEvent.builder().id(null)
            );
        }

        @Test
        void blankId_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> SseEvent.builder().id("   ")
            );
        }

        @Test
        void idContainingNul_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> SseEvent.builder().id("abc\0def")
            );
        }

        @Test
        void idLongerThan512Characters_throwsStringLengthOutsideRangeException() {
            assertThrows(
                    StringLengthOutsideRangeException.class,
                    () -> SseEvent.builder().id("a".repeat(513))
            );
        }

        @Test
        void maxLengthId_isAccepted() {
            SseEvent event = SseEvent.builder()
                    .id("a".repeat(512))
                    .data("payload")
                    .build();

            assertTrue(event.id().isPresent());
            assertEquals("a".repeat(512), event.id().get());
        }
    }

    @Nested
    class Event {
        @Test
        void nullEvent_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> SseEvent.builder().event(null)
            );
        }

        @Test
        void blankEvent_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> SseEvent.builder().event("  ")
            );
        }

        @Test
        void eventContainingLineFeed_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> SseEvent.builder().event("line1\nline2")
            );
        }

        @Test
        void eventContainingCarriageReturn_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> SseEvent.builder().event("line1\rline2")
            );
        }

        @Test
        void validEvent_returnsSameBuilderAndIsApplied() {
            SseEventBuilder builder = SseEvent.builder();

            SseEventBuilder returned = builder.event("message");

            SseEvent event = builder
                    .data("payload")
                    .build();

            assertSame(builder, returned);
            assertTrue(event.event().isPresent());
            assertEquals("message", event.event().get());
        }
    }

    @Nested
    class Data {
        @Test
        void nullData_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> SseEvent.builder().data(null)
            );
        }

        @Test
        void emptyData_isAccepted() {
            SseEvent event = SseEvent.builder()
                    .data("")
                    .build();

            assertEquals("", event.data());
        }

        @Test
        void multilineData_isAccepted() {
            SseEvent event = SseEvent.builder()
                    .data("first line\nsecond line\rthird line")
                    .build();

            assertEquals("first line\nsecond line\rthird line", event.data());
        }
    }

    @Nested
    class Retry {
        @Test
        void nullRetry_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> SseEvent.builder().retry(null)
            );
        }

        @Test
        void negativeRetry_throwsDurationOutsideRangeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> SseEvent.builder().retry(Duration.ofMillis(-1))
            );
        }

        @Test
        void zeroRetry_isAccepted() {
            SseEvent event = SseEvent.builder()
                    .retry(Duration.ZERO)
                    .data("payload")
                    .build();

            assertTrue(event.retry().isPresent());
            assertEquals(Duration.ZERO, event.retry().get());
        }
    }

    @Nested
    class Build {
        @Test
        void dataNotSet_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> SseEvent.builder().build()
            );

            assertEquals(NULL_DATA_MESSAGE, ex.getMessage());
        }

        @Test
        void onlyRequiredFieldSet_buildsEventWithEmptyOptionalFields() {
            SseEvent event = SseEvent.builder()
                    .data("payload")
                    .build();

            assertFalse(event.id().isPresent());
            assertFalse(event.event().isPresent());
            assertFalse(event.retry().isPresent());
            assertEquals("payload", event.data());
        }
    }
}
