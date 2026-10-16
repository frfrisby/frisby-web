package software.frisby.web.server.sse;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.NullValueException;

import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledEventBuilderTest {
    private static final String NULL_INTERVAL_MESSAGE = "The 'interval' value is invalid. The value must not be null.";
    private static final String NULL_SUPPLIER_MESSAGE = "The 'supplier' value is invalid. The value must not be null.";

    private static final Supplier<SseEvent> NO_OP_SUPPLIER = () -> SseEvent.builder().data("payload").build();

    @Nested
    class Interval {
        @Test
        void nullInterval_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ScheduledEvent.builder().interval(null)
            );

            assertEquals(NULL_INTERVAL_MESSAGE, ex.getMessage());
        }

        @Test
        void zeroInterval_throwsDurationOutsideRangeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> ScheduledEvent.builder().interval(Duration.ZERO)
            );
        }

        @Test
        void negativeInterval_throwsDurationOutsideRangeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> ScheduledEvent.builder().interval(Duration.ofMillis(-1))
            );
        }

        @Test
        void positiveInterval_isAccepted() {
            ScheduledEvent event = ScheduledEvent.builder()
                    .interval(Duration.ofMinutes(15))
                    .supplier(NO_OP_SUPPLIER)
                    .build();

            assertEquals(Duration.ofMinutes(15), event.interval());
        }
    }

    @Nested
    class SupplierField {
        @Test
        void nullSupplier_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ScheduledEvent.builder().supplier(null)
            );

            assertEquals(NULL_SUPPLIER_MESSAGE, ex.getMessage());
        }

        @Test
        void validSupplier_isReturnedUnchanged() {
            ScheduledEvent event = ScheduledEvent.builder()
                    .interval(Duration.ofSeconds(1))
                    .supplier(NO_OP_SUPPLIER)
                    .build();

            assertSame(NO_OP_SUPPLIER, event.supplier());
        }
    }

    @Nested
    class Build {
        @Test
        void intervalNotSet_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ScheduledEvent.builder()
                            .supplier(NO_OP_SUPPLIER)
                            .build()
            );

            assertEquals(NULL_INTERVAL_MESSAGE, ex.getMessage());
        }

        @Test
        void supplierNotSet_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ScheduledEvent.builder()
                            .interval(Duration.ofSeconds(1))
                            .build()
            );

            assertEquals(NULL_SUPPLIER_MESSAGE, ex.getMessage());
        }

        @Test
        void bothFieldsSet_returnsSameBuilderAndRoundTrips() {
            ScheduledEventBuilder builder = ScheduledEvent.builder();

            assertSame(builder, builder.interval(Duration.ofMinutes(15)));
            assertSame(builder, builder.supplier(NO_OP_SUPPLIER));

            ScheduledEvent event = builder.build();

            assertEquals(Duration.ofMinutes(15), event.interval());
            assertSame(NO_OP_SUPPLIER, event.supplier());
        }
    }
}


