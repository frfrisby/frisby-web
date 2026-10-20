package software.frisby.web.vthreads;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.client.sse.SseHandler;
import software.frisby.web.client.sse.SseListener;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.TestLogging;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link SseListener} correctly dispatches its reader task and event handlers onto
 * a caller-supplied virtual-thread executor, configured via
 * {@link software.frisby.web.client.sse.SseListenerBuilder#executor(ExecutorService)} —
 * exactly the usage that method's own javadoc documents as supported.
 * <p>
 * Only reactored into the build on a JDK 21+ toolchain — see the root POM's
 * {@code virtual-thread-tests} profile — since {@link Executors#newVirtualThreadPerTaskExecutor()}
 * does not exist under the normal {@code --release 17} build.
 */
class SseListenerVirtualThreadsTest {
    private static Server server;
    private static Client client;

    private ExecutorService virtualThreadExecutor;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(c -> c
                        .port(0)
                        .host("localhost")
                        .serializer(JacksonSerializer.builder().build())
                )
                .resources(new VirtualThreadsTestResource())
                .components(TestLogging.forClass(SseListenerVirtualThreadsTest.class))
                .build();

        server.start();

        client = Client.builder()
                .configuration(c -> c
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    @AfterEach
    void shutdownExecutor() {
        if (null != virtualThreadExecutor) {
            virtualThreadExecutor.shutdown();
        }
    }

    @Test
    void eventHandlerCallback_runsOnConfiguredVirtualThreadExecutor() throws InterruptedException {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicReference<Boolean> handlerThreadIsVirtual = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        SseListener listener = SseListener.builder().client(client)
                .path("/virtual-threads/sse")
                .executor(virtualThreadExecutor)
                .onEvent("thread-info", SseHandler.of(message -> {
                    handlerThreadIsVirtual.set(Thread.currentThread().isVirtual());
                    receivedBody.set(message.body());
                    latch.countDown();
                }))
                .build();

        try {
            listener.connectAsync();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(
                    Boolean.TRUE,
                    handlerThreadIsVirtual.get(),
                    "Expected the SSE event handler callback to run on a virtual thread"
            );
            // Happy-path sanity check — the server in this test is deliberately NOT
            // configured with a virtual-thread executor (that's ServerVirtualThreadsTest's
            // concern), so the event itself still carries whatever the resource method
            // observed for its own dispatch thread.
            assertTrue("true".equals(receivedBody.get()) || "false".equals(receivedBody.get()));
        } finally {
            listener.close();
        }
    }
}

