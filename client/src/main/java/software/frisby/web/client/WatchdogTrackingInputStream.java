package software.frisby.web.client;

import software.frisby.web.client.exception.IdleTimeoutException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps a raw response {@link InputStream} so every byte actually read off the wire
 * resets an idle deadline tracked by {@link StreamWatchdog}, and the underlying stream
 * is forcibly closed if that deadline is ever exceeded — bounding a post-first-byte
 * stall that would otherwise leave a caller's thread parked in {@code read()} forever.
 * <p>
 * Registers itself with {@link StreamWatchdog} on construction, using {@code this} as
 * the registry key and {@link #expire()} — not the raw underlying stream directly — as
 * the {@link java.io.Closeable} to invoke on expiry. This indirection is what lets a
 * pending {@code read()} unblocked by the watchdog be translated into a distinguishable
 * {@link IdleTimeoutException} ({@link #expiredByWatchdog} is set <strong>before</strong>
 * the underlying stream is closed, so a {@code read()} already blocked in the underlying
 * stream's own {@code read()} call always observes the flag by the time its own
 * {@link IOException} is thrown and caught here) — rather than whatever generic
 * {@link IOException} the JDK's own transport happens to throw when a socket is closed
 * out from under a blocked reader, which callers (e.g. {@code client-sse}'s reconnect
 * loop) cannot otherwise tell apart from a genuine transport failure.
 * <p>
 * Deregisters on natural end-of-stream ({@code read} returning {@code -1}) or on
 * {@link #close()}, so a caller who properly drains or closes the stream costs the
 * watchdog nothing further. A caller who does neither still eventually has the
 * underlying stream closed by {@link StreamWatchdog}'s sweep — relying on the JDK's own
 * documented {@link InputStream#close()} contract that closing an already-closed stream
 * has no effect, to make a racing watchdog-triggered close and a caller-triggered close
 * both always safe regardless of ordering.
 *
 * @see StreamWatchdog
 */
final class WatchdogTrackingInputStream extends FilterInputStream {
    private final AtomicBoolean expiredByWatchdog;
    private final URI uri;

    WatchdogTrackingInputStream(InputStream in, Duration idleTimeout, URI uri) {
        super(in);
        this.expiredByWatchdog = new AtomicBoolean(false);
        this.uri = uri;
        StreamWatchdog.register(this, idleTimeout, this::expire, uri);
    }

    @Override
    public int read() throws IOException {
        try {
            int result = super.read();

            recordActivityOrDeregister(result);

            return result;
        } catch (IOException e) {
            throw translateIfExpired(e);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            int result = super.read(b, off, len);

            recordActivityOrDeregister(result);

            return result;
        } catch (IOException e) {
            throw translateIfExpired(e);
        }
    }

    @Override
    public void close() throws IOException {
        StreamWatchdog.deregister(this);
        super.close();
    }

    /**
     * Invoked by {@link StreamWatchdog} in place of closing the underlying stream
     * directly — sets {@link #expiredByWatchdog} <strong>before</strong> closing it, so
     * the ordering guarantee described on this class's own Javadoc holds.
     */
    private void expire() throws IOException {
        expiredByWatchdog.set(true);
        in.close();
    }

    private IOException translateIfExpired(IOException e) {
        return expiredByWatchdog.get()
                ? new IdleTimeoutException("GET " + uri + " — stream idle timeout exceeded.", e)
                : e;
    }

    private void recordActivityOrDeregister(int readResult) {
        if (-1 == readResult) {
            StreamWatchdog.deregister(this);
        } else {
            StreamWatchdog.recordActivity(this);
        }
    }
}
