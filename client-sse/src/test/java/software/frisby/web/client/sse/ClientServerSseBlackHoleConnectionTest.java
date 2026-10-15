package software.frisby.web.client.sse;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.client.RetryDelay;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.server.sse.SseEmitter;
import software.frisby.web.test.TestLogging;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link SseListenerBuilder#idleTimeout} against the exact failure mode it
 * was designed for — a real TCP connection that goes <strong>completely silent</strong>
 * (no more bytes, ever, but also no {@code FIN}/{@code RST}/{@link IOException} on either
 * end) — rather than against timing/ordinary-failure races the way
 * {@code ClientServerSseHeartbeatStormTest} and the disabled
 * {@code ClientServerSseImmediateEventRaceTest} do.
 * <p>
 * {@link BlackHoleTcpProxy} sits between the {@code Client} under test and a real
 * {@link Server} (with a real heartbeat-emitting {@link SseEmitter}): the client connects
 * through the proxy, not directly to the server, so {@link BlackHoleTcpProxy#goSilent()} can
 * simulate the dead connection without either side's socket ever actually closing — the
 * same signature a dropped NAT/conntrack entry, a torn-down container network namespace, or
 * a packet-black-holing middlebox would leave behind in production, and the scenario
 * strongly suspected in the real incident this feature was built to guard against (see
 * {@code temp/frisby-web-sse-reconnect-storm-investigation.md}).
 * <p>
 * Two tests, run against the same scenario, make this a genuine before/after comparison
 * rather than a single assertion that could coincidentally pass for an unrelated reason:
 * {@link #idleTimeoutEnabled_recoversFromASilentBlackhole()} proves an explicit,
 * shorter listener-specific override forces a reconnect and the connection recovers;
 * {@link #idleTimeoutDisabled_fallsBackToTheClientsOwnLongerIdleTimeout()} proves
 * that never calling {@link SseListenerBuilder#idleTimeout} at all — the only way to opt
 * out of a listener-specific override now that {@link Duration#ZERO} is rejected as
 * invalid input like every other positive-duration setting — leaves the same blackholed
 * connection unrecovered within a window comfortably shorter than the {@code Client}'s own
 * (longer) {@code idleTimeout} default that applies instead, demonstrating the recovery
 * above is actually attributable to the shorter override, not some other mechanism.
 */
class ClientServerSseBlackHoleConnectionTest {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(100);
    private static final Duration IDLE_TIMEOUT = Duration.ofMillis(500);

    private Server server;
    private BlackHoleTcpProxy proxy;
    private Client client;

    @AfterEach
    void tearDown() {
        if (null != proxy) {
            proxy.close();
        }

        if (null != server) {
            server.stop();
        }
    }

    private void startServerAndProxy() throws IOException {
        server = Server.builder()
                .configuration(configuration -> configuration
                        .port(0)
                        .host("localhost")
                        .serializer(JacksonSerializer.builder().build())
                )
                .resources(new HeartbeatOnlyResource())
                .components(TestLogging.forClass(ClientServerSseBlackHoleConnectionTest.class))
                .build();

        server.start();

        proxy = new BlackHoleTcpProxy(server.uri().getHost(), server.uri().getPort());
    }

    private Client buildClientThroughProxy() {
        return Client.builder()
                .configuration(configuration -> configuration
                        .uri(URI.create("http://localhost:" + proxy.localPort()))
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();
    }

    @Test
    void idleTimeoutEnabled_recoversFromASilentBlackhole() throws IOException, InterruptedException {
        startServerAndProxy();
        client = buildClientThroughProxy();

        List<SseReconnectEvent> reconnects = new CopyOnWriteArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        try (SseListener listener = SseListener.builder().client(client)
                .path("/blackhole/stream")
                .idleTimeout(IDLE_TIMEOUT)
                .reconnectDelay(RetryDelay.exponential(Duration.ofMillis(50), Duration.ofSeconds(1)))
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onReconnect(SseReconnectEvent event) {
                        reconnects.add(event);
                    }

                    @Override
                    public void onError(SseErrorEvent error) {
                        errorCount.incrementAndGet();
                    }
                })
                .build()) {
            listener.connectAsync();

            assertTrue(
                    awaitAcceptedConnections(1, Duration.ofSeconds(10)),
                    "Expected the first connection to establish normally"
            );

            // Silencing as soon as the proxy has merely accepted the TCP connection is
            // too early -- acceptedConnectionCount() only reflects ServerSocket#accept()
            // completing, well before the upstream server's response (not even headers)
            // has actually been relayed back. goSilent() stops the upstream-to-client
            // relay direction outright, including bytes not yet relayed -- silencing
            // before any byte has reached the client would permanently strand that very
            // first response, turning this into a pre-first-byte HttpTimeoutException
            // (reported as SseReconnectCause.FAILURE) instead of the post-first-byte
            // staleness this test exists to exercise. Waiting for at least one relayed
            // byte first guarantees the client has genuinely received (part of) its
            // response before the connection goes silent.
            assertTrue(
                    awaitRelayedBytes(1, Duration.ofSeconds(10)),
                    "Expected at least one byte to actually reach the client before silencing the connection"
            );

            proxy.goSilent();

            assertTrue(
                    awaitAcceptedConnections(2, Duration.ofSeconds(10)),
                    "Expected the watchdog to force a second connection attempt through the proxy after the "
                            + "first one went silent; saw " + proxy.acceptedConnectionCount() + " connection(s)"
            );
        }

        assertTrue(
                reconnects.stream().anyMatch(event -> SseReconnectCause.STALE_CONNECTION == event.cause()),
                "Expected at least one onReconnect with cause STALE_CONNECTION, saw: " + reconnects
        );
        assertEquals(
                0,
                errorCount.get(),
                "A watchdog-forced reconnect is a deliberate disconnect, not a transport failure -- "
                        + "onError should never fire for it"
        );
    }

    @Test
    void idleTimeoutDisabled_fallsBackToTheClientsOwnLongerIdleTimeout() throws IOException, InterruptedException {
        startServerAndProxy();
        client = buildClientThroughProxy();

        List<SseReconnectEvent> reconnects = new CopyOnWriteArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        try (SseListener listener = SseListener.builder().client(client)
                .path("/blackhole/stream")
                // Never calling idleTimeout(...) at all is the only way to opt out of a
                // listener-specific override -- this client's own configured idleTimeout
                // (defaulting to its 5s readTimeout, set in buildClientThroughProxy()
                // above) applies instead.
                .onEvent("message", SseHandler.of(message -> {
                }))
                .observer(new SseListenerObserver() {
                    @Override
                    public void onReconnect(SseReconnectEvent event) {
                        reconnects.add(event);
                    }

                    @Override
                    public void onError(SseErrorEvent error) {
                        errorCount.incrementAndGet();
                    }
                })
                .build()) {
            listener.connectAsync();

            assertTrue(
                    awaitAcceptedConnections(1, Duration.ofSeconds(10)),
                    "Expected the first connection to establish normally"
            );

            // See the identical wait in idleTimeoutEnabled_recoversFromASilentBlackhole
            // above for why acceptedConnectionCount() alone is too early a signal here.
            assertTrue(
                    awaitRelayedBytes(1, Duration.ofSeconds(10)),
                    "Expected at least one byte to actually reach the client before silencing the connection"
            );

            proxy.goSilent();

            // Comfortably shorter than the Client's own 5s idleTimeout default (fed by
            // readTimeout, since idleTimeout is never explicitly configured here) -- if
            // the client-level watchdog were somehow already acting within this window,
            // the blackholed connection would recover before this assertion runs. This
            // window intentionally stays well under 5s so this test is not racing that
            // separate, still-active guard; it exists purely to prove the
            // listener-specific override itself is a no-op for Duration.ZERO, not that
            // the connection is left unprotected forever.
            Thread.sleep(IDLE_TIMEOUT.toMillis() * 4);

            assertEquals(
                    1,
                    proxy.acceptedConnectionCount(),
                    "Expected no recovery yet within this short window -- never calling idleTimeout(...) "
                            + "applies no listener-specific override, and this window is comfortably shorter "
                            + "than the Client's own idleTimeout default that applies instead"
            );
        }

        assertEquals(List.of(), reconnects);
        assertEquals(0, errorCount.get());
    }

    private boolean awaitAcceptedConnections(int expectedCount, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (proxy.acceptedConnectionCount() >= expectedCount) {
                return true;
            }

            Thread.sleep(20L);
        }

        return proxy.acceptedConnectionCount() >= expectedCount;
    }

    private boolean awaitRelayedBytes(long expectedCount, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (proxy.relayedToClientByteCount() >= expectedCount) {
                return true;
            }

            Thread.sleep(20L);
        }

        return proxy.relayedToClientByteCount() >= expectedCount;
    }

    @Path("/blackhole")
    public static final class HeartbeatOnlyResource {
        @GET
        @Path("/stream")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        public void stream(@Context SseEventSink sink, @Context Sse sse) {
            try (SseEmitter emitter = SseEmitter.builder()
                    .sink(sink)
                    .sse(sse)
                    .heartbeat(HEARTBEAT_INTERVAL)
                    .build()) {
                long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);

                while (emitter.isOpen() && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}





