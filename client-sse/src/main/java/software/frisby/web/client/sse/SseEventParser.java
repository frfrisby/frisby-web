package software.frisby.web.client.sse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Synchronous, buffering parser for the {@code text/event-stream} wire format defined by
 * the Server-Sent Events specification.
 * <p>
 * Reads lines from an underlying {@link InputStream} and assembles them into
 * {@link RawSseEvent} instances. {@link #next()} blocks until either a complete event
 * has been assembled or the stream reaches end-of-file, so this class has no threading
 * concerns of its own — the connection layer's reader thread calls {@link #next()} in a
 * loop.
 * <p>
 * <strong>Simplification vs. the full EventSource algorithm:</strong> the {@code id}
 * field is <em>not</em> sticky across events. The full specification carries a last-seen
 * {@code id} forward to subsequent events that omit an {@code id:} line; since
 * {@link RawSseEvent#id()} models {@code id} per-event rather than as connection-level
 * state, tracking the last received {@code id} across events (for {@code Last-Event-ID}
 * reconnect purposes) is the responsibility of the connection layer, not this parser.
 * <p>
 * {@link RawSseEvent#receivedAt()} is stamped here, in {@link #dispatch()}, the moment an
 * event finishes assembling (the blank-line terminator is read) — this is the true
 * wire-receipt instant, unaffected by any buffering/batching delay further downstream in
 * the dispatch pipeline.
 */
final class SseEventParser {
    private static final String COMMENT_PREFIX = ":";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_EVENT = "event";
    private static final String FIELD_ID = "id";
    private static final String FIELD_RETRY = "retry";
    private static final char FIELD_VALUE_SEPARATOR = ':';
    private static final char NUL = '\0';
    private static final char LINE_FEED = '\n';

    private final BufferedReader reader;
    private final StringBuilder dataBuffer = new StringBuilder();

    private String idBuffer;
    private String eventBuffer;
    private Long retryMillisBuffer;
    private boolean hasData;

    SseEventParser(InputStream in) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /**
     * Blocks until a complete event has been assembled or the stream ends.
     * <p>
     * A trailing, incomplete event at stream close (no final blank-line terminator) is
     * discarded rather than delivered partially, per the specification.
     *
     * @return The next assembled event, or {@link Optional#empty()} if the stream has
     * ended with no further complete events available.
     * @throws IOException if the underlying stream read fails.
     */
    Optional<RawSseEvent> next() throws IOException {
        String line;

        while (null != (line = reader.readLine())) {
            if (line.isEmpty()) {
                Optional<RawSseEvent> event = dispatch();

                if (event.isPresent()) {
                    return event;
                }

                continue;
            }

            if (line.startsWith(COMMENT_PREFIX)) {
                continue;
            }

            processField(line);
        }

        return Optional.empty();
    }

    private void processField(String line) {
        int separator = line.indexOf(FIELD_VALUE_SEPARATOR);
        String field = -1 == separator ? line : line.substring(0, separator);
        String value = -1 == separator ? "" : stripSingleLeadingSpace(line.substring(separator + 1));

        switch (field) {
            case FIELD_DATA -> {
                dataBuffer.append(value).append(LINE_FEED);
                hasData = true;
            }
            case FIELD_EVENT -> eventBuffer = value;
            case FIELD_ID -> {
                if (-1 == value.indexOf(NUL)) {
                    idBuffer = value;
                }
            }
            case FIELD_RETRY -> {
                if (isAsciiDigits(value)) {
                    retryMillisBuffer = Long.parseLong(value);
                }
            }
            default -> {
                // Unrecognized field name — silently ignored, per the SSE specification.
            }
        }
    }

    private Optional<RawSseEvent> dispatch() {
        if (!hasData) {
            reset();
            return Optional.empty();
        }

        // Invariant: hasData is only set to true when at least one "data:" line has been
        // appended, and every append always adds a trailing LINE_FEED — so dataBuffer is
        // guaranteed to end with '\n' whenever hasData is true.  Unconditionally strip it
        // rather than checking endsWith(), which would be an always-true, untestable branch.
        String data = dataBuffer.substring(0, dataBuffer.length() - 1);

        RawSseEvent event = new RawSseEvent(
                Optional.ofNullable(idBuffer),
                Optional.ofNullable(eventBuffer),
                data,
                Optional.ofNullable(retryMillisBuffer).map(Duration::ofMillis),
                Instant.now()
        );

        reset();

        return Optional.of(event);
    }

    private void reset() {
        dataBuffer.setLength(0);
        idBuffer = null;
        eventBuffer = null;
        retryMillisBuffer = null;
        hasData = false;
    }

    private static String stripSingleLeadingSpace(String value) {
        return value.startsWith(" ") ? value.substring(1) : value;
    }

    private static boolean isAsciiDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }

        return true;
    }
}


