package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.NumericValueOutsideRangeException;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultServerConfigurationBuilderTest {
    private static final long FOUR_MB = 4L * 1024L * 1024L;

    // -------------------------------------------------------------------------
    // port
    // -------------------------------------------------------------------------

    @Nested
    class Port {
        @Test
        void portBelowZero_throwsException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> ServerConfiguration.builder().port(-1)
            );
        }

        @Test
        void portAboveMaximum_throwsException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> ServerConfiguration.builder().port(65536)
            );
        }

        @Test
        void minimumPort_isAccepted() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(1)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertEquals(1, config.port());
        }

        @Test
        void maximumPort_isAccepted() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(65535)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertEquals(65535, config.port());
        }
    }

    // -------------------------------------------------------------------------
    // host
    // -------------------------------------------------------------------------

    @Nested
    class Host {
        @Test
        void blankHost_throwsException() {
            assertThrows(
                    RuntimeException.class,
                    () -> ServerConfiguration.builder().host("   ")
            );
        }

        @Test
        void defaultHost_isAllInterfaces() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertEquals("0.0.0.0", config.host());
        }

        @Test
        void customHost_isApplied() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .host("localhost")
                    .serializer(new TestJsonSerializer())
                    .build();

            assertEquals("localhost", config.host());
        }
    }

    // -------------------------------------------------------------------------
    // maxRequestSize
    // -------------------------------------------------------------------------

    @Nested
    class MaxRequestSize {
        @Test
        void zeroMaxRequestSize_throwsException() {
            assertThrows(
                    RuntimeException.class,
                    () -> ServerConfiguration.builder().maxRequestSize(0L)
            );
        }

        @Test
        void negativeMaxRequestSize_throwsException() {
            assertThrows(
                    RuntimeException.class,
                    () -> ServerConfiguration.builder().maxRequestSize(-1L)
            );
        }

        @Test
        void defaultMaxRequestSize_is4MB() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertEquals(FOUR_MB, config.maxRequestSize());
        }

        @Test
        void customMaxRequestSize_isApplied() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .maxRequestSize(1024L)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertEquals(1024L, config.maxRequestSize());
        }
    }

    // -------------------------------------------------------------------------
    // gzip
    // -------------------------------------------------------------------------

    @Nested
    class Gzip {
        @Test
        void gzip_defaultsToFalse() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertFalse(config.gzip());
        }

        @Test
        void gzip_canBeEnabled() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .gzip()
                    .build();

            assertTrue(config.gzip());
        }
    }

    // -------------------------------------------------------------------------
    // serializer
    // -------------------------------------------------------------------------

    @Nested
    class Serializer {
        @Test
        void nullSerializer_throwsException() {
            assertThrows(
                    RuntimeException.class,
                    () -> ServerConfiguration.builder()
                            .port(8080)
                            .serializer(null)
            );
        }
    }

    // -------------------------------------------------------------------------
    // ssl
    // -------------------------------------------------------------------------

    @Nested
    class Ssl {
        @Test
        void ssl_defaultsToEmpty() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertTrue(config.ssl().isEmpty());
        }

        @Test
        void nullSslContext_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerConfiguration.builder().ssl(null)
            );
        }

        @Test
        void validSslContext_isApplied() throws Exception {
            SSLContext sslContext = SSLContext.getDefault();

            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8443)
                    .serializer(new TestJsonSerializer())
                    .ssl(sslContext)
                    .build();

            assertTrue(config.ssl().isPresent());
            assertEquals(sslContext, config.ssl().get());
        }

        @Test
        void noArgSsl_usesDefaultSslContext() throws Exception {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8443)
                    .serializer(new TestJsonSerializer())
                    .ssl()
                    .build();

            assertTrue(config.ssl().isPresent());
            assertEquals(SSLContext.getDefault(), config.ssl().get());
        }
    }

    // -------------------------------------------------------------------------
    // logging
    // -------------------------------------------------------------------------

    @Nested
    class Logging {
        @Test
        void customMaxBodySize_isApplied() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .logging(
                            ServerLoggingConfiguration.builder()
                                    .maxBodySize(1024)
                                    .build()
                    )
                    .build();

            assertEquals(1024, config.logging().maxBodySize());
        }

        @Test
        void zeroMaxBodySize_disablesBodyLogging() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .logging(
                            ServerLoggingConfiguration.builder()
                                    .maxBodySize(0)
                                    .build()
                    )
                    .build();

            assertEquals(0, config.logging().maxBodySize());
        }

        @Test
        void lambdaOverload_isApplied() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .logging(l -> l.maxBodySize(2048))
                    .build();

            assertEquals(2048, config.logging().maxBodySize());
        }
    }

    // -------------------------------------------------------------------------
    // maxConcurrentRequests
    // -------------------------------------------------------------------------

    @Nested
    class MaxConcurrentRequests {
        @Test
        void zeroMaxConcurrentRequests_throwsException() {
            assertThrows(
                    RuntimeException.class,
                    () -> ServerConfiguration.builder()
                            .port(8080)
                            .serializer(new TestJsonSerializer())
                            .maxConcurrentRequests(0)
            );
        }

        @Test
        void negativeMaxConcurrentRequests_throwsException() {
            assertThrows(
                    RuntimeException.class,
                    () -> ServerConfiguration.builder()
                            .port(8080)
                            .serializer(new TestJsonSerializer())
                            .maxConcurrentRequests(-1)
            );
        }

        @Test
        void maxConcurrentRequests_defaultsToAvailableProcessorsTimesTwenty() {
            int expected = Runtime.getRuntime().availableProcessors() * 20;

            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertEquals(expected, config.maxConcurrentRequests());
        }

        @Test
        void customMaxConcurrentRequests_isApplied() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .maxConcurrentRequests(200)
                    .build();

            assertEquals(200, config.maxConcurrentRequests());
        }

        @Test
        void minimumMaxConcurrentRequests_isAccepted() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .maxConcurrentRequests(1)
                    .build();

            assertEquals(1, config.maxConcurrentRequests());
        }
    }

    // -------------------------------------------------------------------------
    // executor
    // -------------------------------------------------------------------------

    @Nested
    class ExecutorTests {
        @Test
        void executor_defaultsToEmpty() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertFalse(config.executor().isPresent());
        }

        @Test
        void nullExecutor_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerConfiguration.builder()
                            .port(8080)
                            .serializer(new TestJsonSerializer())
                            .executor(null)
            );
        }

        @Test
        void customExecutor_isApplied() {
            var pool = Executors.newFixedThreadPool(2);

            try {
                ServerConfiguration config = ServerConfiguration.builder()
                        .port(8080)
                        .serializer(new TestJsonSerializer())
                        .executor(pool)
                        .build();

                assertTrue(config.executor().isPresent());
                assertEquals(pool, config.executor().get());
            } finally {
                pool.shutdown();
            }
        }
    }

    // -------------------------------------------------------------------------
    // http2
    // -------------------------------------------------------------------------

    @Nested
    class Http2 {
        @Test
        void http2_defaultsToFalse() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertFalse(config.http2());
        }

        @Test
        void http2_canBeEnabled() throws Exception {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8443)
                    .serializer(new TestJsonSerializer())
                    .ssl(SSLContext.getDefault())
                    .http2()
                    .build();

            assertTrue(config.http2());
        }

        @Test
        void http2WithoutSsl_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> ServerConfiguration.builder()
                            .port(8443)
                            .serializer(new TestJsonSerializer())
                            .http2()
                            .build()
            );
        }
    }

    // -------------------------------------------------------------------------
    // cors
    // -------------------------------------------------------------------------

    @Nested
    class Cors {
        @Test
        void cors_defaultsToEmpty() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertFalse(config.cors().isPresent());
        }

        @Test
        void nullCors_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerConfiguration.builder()
                            .port(8080)
                            .serializer(new TestJsonSerializer())
                            .cors(null)
            );
        }

        @Test
        void validCors_isApplied() {
            CorsConfiguration cors = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET")
                    .build();

            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .cors(cors)
                    .build();

            assertTrue(config.cors().isPresent());
            assertEquals(cors, config.cors().get());
        }
    }

    // -------------------------------------------------------------------------
    // stopTimeout
    // -------------------------------------------------------------------------

    @Nested
    class StopTimeoutTests {
        @Test
        void nullStopTimeout_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ServerConfiguration.builder()
                            .port(8080)
                            .serializer(new TestJsonSerializer())
                            .stopTimeout(null)
                            .build()
            );
        }

        @Test
        void zeroStopTimeout_throwsException() {
            assertThrows(
                    RuntimeException.class,
                    () -> ServerConfiguration.builder()
                            .port(8080)
                            .serializer(new TestJsonSerializer())
                            .stopTimeout(Duration.ZERO)
                            .build()
            );
        }

        @Test
        void negativeStopTimeout_throwsException() {
            assertThrows(
                    RuntimeException.class,
                    () -> ServerConfiguration.builder()
                            .port(8080)
                            .serializer(new TestJsonSerializer())
                            .stopTimeout(Duration.ofSeconds(-1))
                            .build()
            );
        }

        @Test
        void stopTimeout_defaultsToEmpty() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .build();

            assertFalse(config.stopTimeout().isPresent());
        }

        @Test
        void stopTimeout_isApplied() {
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8080)
                    .serializer(new TestJsonSerializer())
                    .stopTimeout(Duration.ofSeconds(30))
                    .build();

            assertTrue(config.stopTimeout().isPresent());
            assertEquals(Duration.ofSeconds(30), config.stopTimeout().get());
        }
    }
}

