package software.frisby.web.client.exception;

import java.io.IOException;
import java.io.Serial;

/**
 * Thrown from a stream-producing spec's ({@code DownloadSpec}, {@code SseSpec}) response
 * {@link java.io.InputStream} when a pending {@code read()} is unblocked because the
 * configured {@code idleTimeout} elapsed with no activity on the stream — the
 * post-first-byte half of {@code idleTimeout}'s job, enforced by the shared
 * {@code StreamWatchdog}.
 * <p>
 * An {@link IOException} subtype rather than a {@code RuntimeException} — unlike the
 * {@link HttpRequestException} hierarchy — specifically because this is thrown from
 * inside {@link java.io.InputStream#read()} itself, which must stay compatible with
 * ordinary, well-behaved I/O code that already catches {@link IOException}. Callers who
 * want to distinguish this from any other transport-level read failure (e.g. {@code
 * client-sse}'s reconnect loop, which reports a distinct
 * {@code SseReconnectCause.STALE_CONNECTION} for it) can catch this type specifically,
 * before a broader {@code catch (IOException e)}.
 *
 * @see software.frisby.web.client.ClientConfiguration#idleTimeout()
 */
public final class IdleTimeoutException extends IOException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a detail message.
     *
     * @param message The detail message.
     */
    public IdleTimeoutException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a detail message and the underlying cause — typically
     * the {@link IOException} the pending {@code read()} was unblocked with once the
     * watchdog closed the underlying stream.
     *
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public IdleTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

