package software.frisby.web.client.sse;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseEventParserTest {
    private static SseEventParser parserFor(String wireFormat) {
        return new SseEventParser(inputStreamFor(wireFormat));
    }

    private static InputStream inputStreamFor(String wireFormat) {
        return new ByteArrayInputStream(wireFormat.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Basic single-event framing
    // -------------------------------------------------------------------------

    @Nested
    class SingleEvent {
        @Test
        void allFieldsPresent_isFullyAssembled() throws IOException {
            SseEventParser parser = parserFor("id: 47\nevent: file-ready\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.of("47"), event.get().id());
            assertEquals(Optional.of("file-ready"), event.get().event());
            assertEquals("hello", event.get().data());
            assertEquals(Optional.empty(), event.get().retry());
        }

        @Test
        void onlyDataField_defaultsAppliedToIdAndEvent() throws IOException {
            SseEventParser parser = parserFor("data: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.empty(), event.get().id());
            assertEquals(Optional.empty(), event.get().event());
            assertEquals("hello", event.get().data());
        }

        @Test
        void multiLineData_isJoinedWithNewlines() throws IOException {
            SseEventParser parser = parserFor("data: line one\ndata: line two\ndata: line three\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals("line one\nline two\nline three", event.get().data());
        }

        @Test
        void noBlankLineTerminator_atStreamClose_isDiscarded() throws IOException {
            SseEventParser parser = parserFor("id: 1\nevent: incomplete\ndata: never dispatched");

            Optional<RawSseEvent> event = parser.next();

            assertFalse(event.isPresent());
        }

        @Test
        void blankLineWithNoPrecedingData_doesNotDispatch() throws IOException {
            SseEventParser parser = parserFor("\n\ndata: real event\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals("real event", event.get().data());
        }
    }

    // -------------------------------------------------------------------------
    // receivedAt() — stamped at parse time, not at dispatch time
    // -------------------------------------------------------------------------

    @Nested
    class ReceivedAt {
        @Test
        void isStampedAtParseTime_withinABoundedWindowOfTheCall() throws IOException {
            SseEventParser parser = parserFor("data: hello\n\n");

            Instant before = Instant.now();
            Optional<RawSseEvent> event = parser.next();
            Instant after = Instant.now();

            assertTrue(event.isPresent());
            Instant receivedAt = event.get().receivedAt();

            assertFalse(receivedAt.isBefore(before), "Expected receivedAt not to precede the call to next()");
            assertFalse(receivedAt.isAfter(after), "Expected receivedAt not to follow the call to next()");
        }

        @Test
        void twoEventsInOneRead_eachGetsItsOwnNonDecreasingTimestamp() throws IOException {
            SseEventParser parser = parserFor("data: first\n\ndata: second\n\n");

            Optional<RawSseEvent> first = parser.next();
            Optional<RawSseEvent> second = parser.next();

            assertTrue(first.isPresent());
            assertTrue(second.isPresent());
            assertFalse(
                    second.get().receivedAt().isBefore(first.get().receivedAt()),
                    "Expected the second event's receivedAt to not precede the first's"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Comments
    // -------------------------------------------------------------------------

    @Nested
    class Comments {
        @Test
        void bareKeepAliveComment_isSilentlyDiscarded() throws IOException {
            SseEventParser parser = parserFor(": keep-alive\n\ndata: after comment\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals("after comment", event.get().data());
        }

        @Test
        void commentInterleavedBetweenFields_isIgnored() throws IOException {
            SseEventParser parser = parserFor("id: 1\n: a mid-event comment\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.of("1"), event.get().id());
            assertEquals("hello", event.get().data());
        }
    }

    // -------------------------------------------------------------------------
    // retry field
    // -------------------------------------------------------------------------

    @Nested
    class RetryField {
        @Test
        void numericValue_isParsedAsMillisecondDuration() throws IOException {
            SseEventParser parser = parserFor("retry: 5000\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.of(Duration.ofMillis(5000)), event.get().retry());
        }

        @Test
        void zeroValue_isParsedAsZeroDuration() throws IOException {
            SseEventParser parser = parserFor("retry: 0\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.of(Duration.ZERO), event.get().retry());
        }

        @Test
        void nonNumericValue_isIgnoredNotAParseFailure() throws IOException {
            SseEventParser parser = parserFor("retry: not-a-number\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.empty(), event.get().retry());
        }

        @Test
        void negativeValue_isIgnoredNotAParseFailure() throws IOException {
            SseEventParser parser = parserFor("retry: -10\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.empty(), event.get().retry());
        }

        @Test
        void emptyValue_isIgnored() throws IOException {
            SseEventParser parser = parserFor("retry:\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.empty(), event.get().retry());
        }
    }

    // -------------------------------------------------------------------------
    // id field
    // -------------------------------------------------------------------------

    @Nested
    class IdField {
        @Test
        void valueContainingNulCharacter_isIgnored() throws IOException {
            SseEventParser parser = parserFor("id: bad\0id\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.empty(), event.get().id());
        }
    }

    // -------------------------------------------------------------------------
    // Unrecognized fields
    // -------------------------------------------------------------------------

    @Nested
    class UnrecognizedFields {
        @Test
        void unknownFieldName_isSilentlyIgnored() throws IOException {
            SseEventParser parser = parserFor("custom-field: whatever\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals("hello", event.get().data());
        }
    }

    // -------------------------------------------------------------------------
    // Multiple events
    // -------------------------------------------------------------------------

    @Nested
    class MultipleEvents {
        @Test
        void backToBackInASingleRead_bothFullyAssembled() throws IOException {
            SseEventParser parser = parserFor("data: first\n\ndata: second\n\n");

            Optional<RawSseEvent> first = parser.next();
            Optional<RawSseEvent> second = parser.next();
            Optional<RawSseEvent> third = parser.next();

            assertTrue(first.isPresent());
            assertEquals("first", first.get().data());
            assertTrue(second.isPresent());
            assertEquals("second", second.get().data());
            assertFalse(third.isPresent());
        }
    }

    // -------------------------------------------------------------------------
    // Line endings
    // -------------------------------------------------------------------------

    @Nested
    class LineEndings {
        @Test
        void crlfLineEndings_areHandled() throws IOException {
            SseEventParser parser = parserFor("id: 1\r\nevent: greeting\r\ndata: hello\r\n\r\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.of("1"), event.get().id());
            assertEquals(Optional.of("greeting"), event.get().event());
            assertEquals("hello", event.get().data());
        }

        @Test
        void lfLineEndings_areHandled() throws IOException {
            SseEventParser parser = parserFor("id: 1\nevent: greeting\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.of("1"), event.get().id());
            assertEquals(Optional.of("greeting"), event.get().event());
            assertEquals("hello", event.get().data());
        }
    }

    // -------------------------------------------------------------------------
    // Field value whitespace handling
    // -------------------------------------------------------------------------

    @Nested
    class FieldValueWhitespace {
        @Test
        void singleLeadingSpaceAfterColon_isStripped() throws IOException {
            SseEventParser parser = parserFor("data: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals("hello", event.get().data());
        }

        @Test
        void noLeadingSpaceAfterColon_valueUnchanged() throws IOException {
            SseEventParser parser = parserFor("data:hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals("hello", event.get().data());
        }

        @Test
        void multipleLeadingSpacesAfterColon_onlyFirstIsStripped() throws IOException {
            SseEventParser parser = parserFor("data:  hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(" hello", event.get().data());
        }

        @Test
        void fieldWithNoColon_isTreatedAsFieldNameWithEmptyValue() throws IOException {
            SseEventParser parser = parserFor("data\ndata: hello\n\n");

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals("\nhello", event.get().data());
        }
    }

    // -------------------------------------------------------------------------
    // Streaming behavior — reads split across multiple underlying read() calls
    // -------------------------------------------------------------------------

    @Nested
    class ChunkedReads {
        @Test
        void eventSplitAcrossMultipleUnderlyingReads_isAssembledCorrectly() throws IOException {
            SseEventParser parser = new SseEventParser(
                    new OneByteAtATimeInputStream("id: 1\nevent: chunked\ndata: hello world\n\n")
            );

            Optional<RawSseEvent> event = parser.next();

            assertTrue(event.isPresent());
            assertEquals(Optional.of("1"), event.get().id());
            assertEquals(Optional.of("chunked"), event.get().event());
            assertEquals("hello world", event.get().data());
        }
    }

    /**
     * An {@link InputStream} that only ever returns a single byte per {@code read()} call,
     * used to simulate an event whose bytes arrive across many underlying network reads.
     */
    private static final class OneByteAtATimeInputStream extends InputStream {
        private final byte[] data;
        private int position;

        OneByteAtATimeInputStream(String content) {
            this.data = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int read() {
            if (position >= data.length) {
                return -1;
            }

            return data[position++] & 0xFF;
        }
    }
}

