package software.frisby.web.vthreads;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.Client;
import software.frisby.web.serial.jackson.JacksonSerializer;
import software.frisby.web.server.Server;
import software.frisby.web.test.TestLogging;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves {@link Server} correctly dispatches request-handling onto a caller-supplied
 * virtual-thread executor, configured via
 * {@code ServerConfigurationBuilder#executor(ExecutorService)}.
 * <p>
 * Only reactored into the build on a JDK 21+ toolchain — see the root POM's
 * {@code virtual-thread-tests} profile — since {@link Executors#newVirtualThreadPerTaskExecutor()}
 * does not exist under the normal {@code --release 17} build.
 */
class ServerVirtualThreadsTest {
    private Server server;
    private ExecutorService virtualThreadExecutor;

    @AfterEach
    void stopServer() {
        if (null != server) {
            server.stop();
        }

        if (null != virtualThreadExecutor) {
            virtualThreadExecutor.shutdown();
        }
    }

    /**
     * A plain, default-configured {@link Client} hits a resource method that reports
     * {@link Thread#isVirtual()} for the thread that executed it — proving the
     * virtual-thread executor configured on the {@link Server} is the one Jetty actually
     * dispatches request handling onto, not merely accepted without effect.
     */
    @Test
    void requestHandlingResourceMethod_runsOnConfiguredVirtualThreadExecutor() {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        server = Server.builder()
                .configuration(c -> c
                        .port(0)
                        .host("localhost")
                        .serializer(JacksonSerializer.builder().build())
                        .executor(virtualThreadExecutor)
                )
                .resources(new VirtualThreadsTestResource())
                .components(TestLogging.forClass(ServerVirtualThreadsTest.class))
                .build();

        server.start();

        Client client = Client.builder()
                .configuration(c -> c
                        .uri(server.uri())
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(10))
                        .serializer(JacksonSerializer.builder().build())
                )
                .build();

        Boolean threadIsVirtual = client.get()
                .path("/virtual-threads/ping")
                .send(Boolean.class)
                .body();

        assertEquals(Boolean.TRUE, threadIsVirtual, "Expected the resource method to execute on a virtual thread");
    }
}


