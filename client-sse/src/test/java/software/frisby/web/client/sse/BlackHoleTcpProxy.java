package software.frisby.web.client.sse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A transparent TCP byte-pump proxy that sits between a test's {@code Client} and a real
 * server, able to simulate exactly the failure mode {@code idleTimeout} exists
 * for: a connection that goes <strong>silently dead</strong> — no more bytes, ever, but
 * also no {@code FIN}, no {@code RST}, and no {@link IOException} on either socket — rather
 * than an ordinary transport failure or clean close either side of this library could
 * already detect without a watchdog.
 * <p>
 * Each accepted client connection opens its own matching upstream connection to the real
 * server and relays bytes in both directions on dedicated threads. {@link #goSilent()}
 * flips a per-connection flag that makes the upstream-to-client relay direction simply stop
 * forwarding entirely for every connection accepted <em>so far</em> — it does not touch
 * either socket, so from the client's perspective the connection is still technically open,
 * exactly like a dropped NAT/conntrack entry or a middlebox black-holing packets. Future
 * connections accepted after {@link #goSilent()} is called are unaffected (their own
 * silenced flag starts {@code false}), so a client that reconnects through this same proxy
 * after a stale-connection-triggered disconnect succeeds normally — letting a test observe
 * genuine recovery, not just that the old connection died.
 * <p>
 * Deliberately not a general-purpose, reusable test-support utility (it lives in this
 * module's test sources, not {@code test-support}) — the scope needed here (plain loopback
 * TCP, one relay pair per accepted connection, one global silence switch) is narrow enough
 * that generalizing it further isn't worth the complexity before a second consumer actually
 * needs it.
 */
final class BlackHoleTcpProxy implements AutoCloseable {
    private static final int BUFFER_SIZE = 4096;

    private final ServerSocket serverSocket;
    private final String upstreamHost;
    private final int upstreamPort;
    private final ExecutorService executor;
    private final List<Connection> connections;
    private final AtomicInteger acceptedConnectionCount;
    private final AtomicLong relayedToClientByteCount;
    private final AtomicBoolean closed;

    BlackHoleTcpProxy(String upstreamHost, int upstreamPort) throws IOException {
        this.upstreamHost = upstreamHost;
        this.upstreamPort = upstreamPort;
        this.serverSocket = new ServerSocket(0);
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "blackhole-proxy");
            thread.setDaemon(true);
            return thread;
        });
        this.connections = new CopyOnWriteArrayList<>();
        this.acceptedConnectionCount = new AtomicInteger(0);
        this.relayedToClientByteCount = new AtomicLong(0);
        this.closed = new AtomicBoolean(false);

        executor.submit(this::acceptLoop);
    }

    int localPort() {
        return serverSocket.getLocalPort();
    }

    int acceptedConnectionCount() {
        return acceptedConnectionCount.get();
    }

    /**
     * Total bytes actually relayed from upstream to a client so far, across every
     * connection — i.e. bytes the client has genuinely received, as opposed to bytes
     * merely accepted at the TCP layer. {@link #acceptedConnectionCount()} only reflects
     * {@link ServerSocket#accept()} completing, which happens the instant the TCP
     * handshake finishes and says nothing about whether the upstream server has produced
     * (or this proxy has relayed) a single byte of the actual HTTP response yet — calling
     * {@link #goSilent()} before this counter has advanced past its pre-call value would
     * silence the connection before the client ever receives a response at all, which is
     * indistinguishable, from the client's perspective, from the upstream server itself
     * never responding — a completely different failure mode (a pre-first-byte timeout)
     * than the post-first-byte staleness {@link #goSilent()} exists to simulate. Tests
     * must poll this counter (not {@link #acceptedConnectionCount()}) to confirm the
     * client has actually received its first byte before silencing.
     */
    long relayedToClientByteCount() {
        return relayedToClientByteCount.get();
    }

    /**
     * Silences the upstream-to-client direction of every connection accepted so far,
     * simulating a connection that has gone silently dead from this instant on — no
     * further bytes, but no close/FIN/RST either. Connections accepted after this call
     * are unaffected.
     */
    void goSilent() {
        for (Connection connection : connections) {
            connection.silenced.set(true);
        }
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                Socket upstreamSocket = new Socket(upstreamHost, upstreamPort);
                Connection connection = new Connection(clientSocket, upstreamSocket);

                connections.add(connection);
                acceptedConnectionCount.incrementAndGet();

                executor.submit(() -> relay(
                        connection.clientSocket, connection.upstreamSocket, connection.silenced, false));
                executor.submit(() -> relay(
                        connection.upstreamSocket, connection.clientSocket, connection.silenced, true));
            } catch (IOException e) {
                // Expected on close() tearing down serverSocket -- exit quietly.
                return;
            }
        }
    }

    /**
     * Pumps bytes from {@code source} to {@code destination} until {@code source} reaches
     * end-of-stream or either socket closes. When {@code checkSilenced} is {@code true}
     * (the upstream-to-client direction only) and {@code silenced} is set, this thread
     * stops forwarding entirely — it neither reads further from {@code source} nor writes
     * to {@code destination} — simulating a connection that has gone completely silent
     * rather than merely slow.
     */
    private void relay(Socket source, Socket destination, AtomicBoolean silenced, boolean checkSilenced) {
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            InputStream in = source.getInputStream();
            OutputStream out = destination.getOutputStream();

            while (true) {
                if (checkSilenced && silenced.get()) {
                    Thread.sleep(50L);
                    continue;
                }

                int read = in.read(buffer);

                if (-1 == read) {
                    return;
                }

                if (checkSilenced && silenced.get()) {
                    // Dropped: silenced flag flipped between the read() call above
                    // returning and this check -- never forward it.
                    continue;
                }

                out.write(buffer, 0, read);
                out.flush();

                if (checkSilenced) {
                    relayedToClientByteCount.addAndGet(read);
                }
            }
        } catch (IOException | InterruptedException e) {
            // Expected once either socket is closed (proxy shutdown, or the client/server
            // ending the connection normally) -- nothing to report.
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            serverSocket.close();
        } catch (IOException e) {
            // Best-effort cleanup only.
        }

        for (Connection connection : connections) {
            closeQuietly(connection.clientSocket);
            closeQuietly(connection.upstreamSocket);
        }

        executor.shutdownNow();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // Best-effort cleanup only.
        }
    }

    private static final class Connection {
        private final Socket clientSocket;
        private final Socket upstreamSocket;
        private final AtomicBoolean silenced;

        private Connection(Socket clientSocket, Socket upstreamSocket) {
            this.clientSocket = clientSocket;
            this.upstreamSocket = upstreamSocket;
            this.silenced = new AtomicBoolean(false);
        }
    }
}

