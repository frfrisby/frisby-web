package software.frisby.web.vthreads;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.TestLogging;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link Client} actually submits work to a caller-supplied virtual-thread
 * executor, configured via {@code ClientConfigurationBuilder#executor(ExecutorService)},
 * rather than merely accepting it without effect.
 * <p>
 * Inspecting {@link Thread#isVirtual()} from inside a chained {@code CompletableFuture}
 * continuation turned out to be an unreliable proof — neither the JDK's "default
 * executor" propagation for the {@code ...Async} method family nor directly polling
 * {@link Thread#getAllStackTraces()} for a live, named thread survives the client
 * module's own internal plain (non-{@code Async}) {@code .handle(...)} wrapping and/or
 * the executor thread's own short lifetime (both confirmed empirically — earlier
 * versions of this test asserting each of those failed reliably). Instead, this test
 * wraps the virtual-thread executor itself and simply counts how many tasks are ever
 * submitted to it via {@link ExecutorService#execute(Runnable)}. Any count greater than
 * zero proves the JDK's {@link java.net.http.HttpClient} genuinely used the configured
 * executor during the request, independent of which specific continuation happens to
 * run on which specific thread.
 * <p>
 * Only reactored into the build on a JDK 21+ toolchain — see the root POM's
 * {@code virtual-thread-tests} profile — since {@link Executors#newVirtualThreadPerTaskExecutor()}
 * does not exist under the normal {@code --release 17} build.
 */
class ClientVirtualThreadsTest {
    private static Server server;

    @BeforeAll
    static void startServer() {
        server = Server.builder()
                .configuration(c -> c
                        .port(0)
                        .host("localhost")
                        .serializer(JacksonSerializer.builder().build())
                )
                .resources(new VirtualThreadsTestResource())
                .components(TestLogging.forClass(ClientVirtualThreadsTest.class))
                .build();

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (null != server) {
            server.stop();
        }
    }

    /**
     * Delegates every submitted task to a real {@code Executors.newVirtualThreadPerTaskExecutor()}
     * — so every task genuinely runs on a virtual thread — while counting how many tasks
     * are ever submitted, via the one method ({@link #execute(Runnable)}) every other
     * {@link ExecutorService} method ultimately funnels through once inherited from
     * {@link AbstractExecutorService}.
     */
    private static final class CountingVirtualThreadExecutor extends AbstractExecutorService {
        private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicInteger submittedTaskCount = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            submittedTaskCount.incrementAndGet();
            delegate.execute(command);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        int submittedTaskCount() {
            return submittedTaskCount.get();
        }
    }

    @Test
    void requestLifecycle_submitsAtLeastOneTaskToConfiguredVirtualThreadExecutor() {
        try (CountingVirtualThreadExecutor countingExecutor = new CountingVirtualThreadExecutor()) {
            Client client = Client.builder()
                    .configuration(c -> c
                            .uri(server.uri())
                            .connectTimeout(Duration.ofSeconds(5))
                            .readTimeout(Duration.ofSeconds(10))
                            .serializer(JacksonSerializer.builder().build())
                            .executor(countingExecutor)
                    )
                    .build();

            HttpResponse<Boolean> response = client.get()
                    .path("/virtual-threads/ping")
                    .send(Boolean.class);

            assertEquals(200, response.statusCode());
            assertTrue(
                    countingExecutor.submittedTaskCount() > 0,
                    "Expected the JDK HttpClient to submit at least one task to the "
                            + "configured virtual-thread executor during the request"
            );
        }
    }
}











