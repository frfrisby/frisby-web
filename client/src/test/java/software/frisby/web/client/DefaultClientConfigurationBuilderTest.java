package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.DuplicateElementsException;
import software.frisby.core.validation.NullValueException;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class DefaultClientConfigurationBuilderTest {
    private static final URI BASE_URI = URI.create("https://api.example.com");
    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration READ = Duration.ofSeconds(30);

    private static final String NULL_URI_MSG = "The 'uri' value is invalid. The value must not be null.";
    private static final String NULL_CONNECT_TIMEOUT_MSG = "The 'connectTimeout' value is invalid. The value must not be null.";
    private static final String NULL_READ_TIMEOUT_MSG = "The 'readTimeout' value is invalid. The value must not be null.";
    private static final String NULL_SERIALIZER_MSG = "The 'serializer' value is invalid. The value must not be null.";
    private static final String NULL_SSL_CONTEXT_MSG = "The 'sslContext' value is invalid. The value must not be null.";
    private static final String NULL_REDIRECT_POLICY_MSG = "The 'redirectPolicy' value is invalid. The value must not be null.";
    private static final String NULL_HTTP_VERSION_MSG = "The 'httpVersion' value is invalid. The value must not be null.";
    private static final String NULL_EXECUTOR_MSG = "The 'executor' value is invalid. The value must not be null.";
    private static final String NULL_LOGGING_MSG = "The 'logging' value is invalid. The value must not be null.";
    private static final String NULL_DECOMPRESSOR_MSG = "The 'decompressor' value is invalid. The value must not be null.";
    private static final String DUPLICATE_DECOMPRESSOR_MSG =
            "The 'decompressors' value is invalid. The value must not contain duplicate elements. Found duplicate: 'gzip'.";

    private static ClientConfiguration validConfig() {
        return ClientConfiguration.builder()
                .uri(BASE_URI)
                .connectTimeout(CONNECT)
                .readTimeout(READ)
                .serializer(new TestJsonSerializer())
                .build();
    }

    // -------------------------------------------------------------------------
    // uri
    // -------------------------------------------------------------------------

    @Nested
    class Uri {
        @Test
        void nullUri_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().uri(null)
            );

            assertEquals(NULL_URI_MSG, ex.getMessage());
        }

        @Test
        void missingUri_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder()
                            .connectTimeout(CONNECT)
                            .readTimeout(READ)
                            .serializer(new TestJsonSerializer())
                            .build()
            );

            assertEquals(NULL_URI_MSG, ex.getMessage());
        }

        @Test
        void uri_isApplied() {
            assertEquals(BASE_URI, validConfig().uri());
        }
    }

    // -------------------------------------------------------------------------
    // connectTimeout
    // -------------------------------------------------------------------------

    @Nested
    class ConnectTimeout {
        @Test
        void nullConnectTimeout_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().connectTimeout(null)
            );

            assertEquals(NULL_CONNECT_TIMEOUT_MSG, ex.getMessage());
        }

        @Test
        void missingConnectTimeout_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder()
                            .uri(BASE_URI)
                            .readTimeout(READ)
                            .serializer(new TestJsonSerializer())
                            .build()
            );

            assertEquals(NULL_CONNECT_TIMEOUT_MSG, ex.getMessage());
        }

        @Test
        void connectTimeout_isApplied() {
            assertEquals(CONNECT, validConfig().connectTimeout());
        }
    }

    // -------------------------------------------------------------------------
    // readTimeout
    // -------------------------------------------------------------------------

    @Nested
    class ReadTimeout {
        @Test
        void nullReadTimeout_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().readTimeout(null)
            );

            assertEquals(NULL_READ_TIMEOUT_MSG, ex.getMessage());
        }

        @Test
        void missingReadTimeout_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder()
                            .uri(BASE_URI)
                            .connectTimeout(CONNECT)
                            .serializer(new TestJsonSerializer())
                            .build()
            );

            assertEquals(NULL_READ_TIMEOUT_MSG, ex.getMessage());
        }

        @Test
        void readTimeout_isApplied() {
            assertEquals(READ, validConfig().readTimeout());
        }
    }

    // -------------------------------------------------------------------------
    // serializer
    // -------------------------------------------------------------------------

    @Nested
    class Serializer {
        @Test
        void nullSerializer_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().serializer(null)
            );

            assertEquals(NULL_SERIALIZER_MSG, ex.getMessage());
        }

        @Test
        void missingSerializer_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder()
                            .uri(BASE_URI)
                            .connectTimeout(CONNECT)
                            .readTimeout(READ)
                            .build()
            );

            assertEquals(NULL_SERIALIZER_MSG, ex.getMessage());
        }

        @Test
        void serializer_isApplied() {
            var serializer = new TestJsonSerializer();

            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(serializer)
                    .build();

            assertEquals(serializer, config.serializer());
        }
    }

    // -------------------------------------------------------------------------
    // sslContext
    // -------------------------------------------------------------------------

    @Nested
    class SslContextMethod {
        @Test
        void nullSslContext_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().sslContext(null)
            );

            assertEquals(NULL_SSL_CONTEXT_MSG, ex.getMessage());
        }

        @Test
        void sslContext_defaultsToEmpty() {
            assertTrue(validConfig().sslContext().isEmpty());
        }

        @Test
        void sslContext_isApplied() throws Exception {
            SSLContext ctx = SSLContext.getDefault();

            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(new TestJsonSerializer())
                    .sslContext(ctx)
                    .build();

            assertTrue(config.sslContext().isPresent());
            assertEquals(ctx, config.sslContext().get());
        }
    }

    // -------------------------------------------------------------------------
    // redirectPolicy
    // -------------------------------------------------------------------------

    @Nested
    class RedirectPolicy {
        @Test
        void nullRedirectPolicy_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().redirectPolicy(null)
            );

            assertEquals(NULL_REDIRECT_POLICY_MSG, ex.getMessage());
        }

        @Test
        void redirectPolicy_defaultsToNormal() {
            assertEquals(HttpClient.Redirect.NORMAL, validConfig().redirectPolicy());
        }

        @Test
        void redirectPolicy_isApplied() {
            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(new TestJsonSerializer())
                    .redirectPolicy(HttpClient.Redirect.NEVER)
                    .build();

            assertEquals(HttpClient.Redirect.NEVER, config.redirectPolicy());
        }
    }

    // -------------------------------------------------------------------------
    // httpVersion
    // -------------------------------------------------------------------------

    @Nested
    class HttpVersion {
        @Test
        void nullHttpVersion_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().httpVersion(null)
            );

            assertEquals(NULL_HTTP_VERSION_MSG, ex.getMessage());
        }

        @Test
        void httpVersion_defaultsToHttp11() {
            assertEquals(HttpClient.Version.HTTP_1_1, validConfig().httpVersion());
        }

        @Test
        void httpVersion_isApplied() {
            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(new TestJsonSerializer())
                    .httpVersion(HttpClient.Version.HTTP_2)
                    .build();

            assertEquals(HttpClient.Version.HTTP_2, config.httpVersion());
        }
    }

    // -------------------------------------------------------------------------
    // decompress
    // -------------------------------------------------------------------------

    @Nested
    class DecompressMethod {
        @Test
        void decompress_defaultsToEmpty() {
            assertTrue(validConfig().decompressors().isEmpty());
        }

        @Test
        void decompress_noArg_addsGzipDecompressor() {
            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(new TestJsonSerializer())
                    .decompress()
                    .build();

            List<ContentDecompressor> decompressors = config.decompressors();

            assertEquals(1, decompressors.size());
            assertEquals("gzip", decompressors.get(0).encoding());
        }

        @Test
        void decompress_acceptEncoding_derivedFromRegisteredEncodings() {
            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(new TestJsonSerializer())
                    .decompress()
                    .decompress(ContentDecompressor.of("br", GZIPInputStream::new))
                    .build();

            assertEquals("gzip, br", DefaultClientConfiguration.acceptEncoding(config.decompressors()));
        }

        @Test
        void decompress_multipleCallsAreAdditive() {
            ContentDecompressor custom = ContentDecompressor.of("br", GZIPInputStream::new);

            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(new TestJsonSerializer())
                    .decompress()
                    .decompress(custom)
                    .build();

            assertEquals(2, config.decompressors().size());
            assertEquals("gzip", config.decompressors().get(0).encoding());
            assertEquals("br", config.decompressors().get(1).encoding());
        }

        @Test
        void decompress_noDecompressors_acceptEncodingIsNull() {
            assertNull(DefaultClientConfiguration.acceptEncoding(validConfig().decompressors()));
        }

        @Test
        void nullDecompressor_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().decompress(null)
            );

            assertEquals(NULL_DECOMPRESSOR_MSG, ex.getMessage());
        }

        @Test
        void duplicateEncoding_throwsDuplicateElementsException() {
            DuplicateElementsException ex = assertThrows(
                    DuplicateElementsException.class,
                    () -> ClientConfiguration.builder()
                            .uri(BASE_URI)
                            .connectTimeout(CONNECT)
                            .readTimeout(READ)
                            .serializer(new TestJsonSerializer())
                            .decompress()
                            .decompress()
                            .build()
            );

            assertEquals(DUPLICATE_DECOMPRESSOR_MSG, ex.getMessage());
        }

        @Test
        void decompressors_listIsUnmodifiable() {
            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(new TestJsonSerializer())
                    .decompress()
                    .build();

            assertThrows(UnsupportedOperationException.class,
                    () -> config.decompressors().add(ContentDecompressor.of("br", s -> InputStream.nullInputStream())));
        }
    }

    // -------------------------------------------------------------------------
    // executor
    // -------------------------------------------------------------------------

    @Nested
    class ExecutorMethod {
        @Test
        void nullExecutor_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().executor(null)
            );

            assertEquals(NULL_EXECUTOR_MSG, ex.getMessage());
        }

        @Test
        void executor_defaultsToEmpty() {
            assertTrue(validConfig().executor().isEmpty());
        }

        @Test
        void executor_isApplied() {
            var pool = Executors.newFixedThreadPool(2);

            try {
                ClientConfiguration config = ClientConfiguration.builder()
                        .uri(BASE_URI)
                        .connectTimeout(CONNECT)
                        .readTimeout(READ)
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
    // logging
    // -------------------------------------------------------------------------

    @Nested
    class LoggingMethod {
        @Test
        void nullLogging_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> ClientConfiguration.builder().logging(null)
            );

            assertEquals(NULL_LOGGING_MSG, ex.getMessage());
        }

        @Test
        void logging_defaultsToDefaultInstance() {
            ClientLoggingConfiguration logging = validConfig().logging();

            assertNotNull(logging);
            assertEquals(DefaultClientLoggingConfigurationBuilder.DEFAULT_MAX_BODY_SIZE, logging.maxBodySize());
            assertTrue(logging.redactedBodyFields().isEmpty());
            assertTrue(logging.redactedHeaders().contains("authorization"));
            assertTrue(logging.redactedHeaders().contains("cookie"));
            assertTrue(logging.redactedHeaders().contains("set-cookie"));
        }

        @Test
        void logging_isApplied() {
            ClientLoggingConfiguration custom = ClientLoggingConfiguration.builder()
                    .maxBodySize(1024)
                    .redactHeaders("X-Api-Key")
                    .redactFields("password")
                    .build();

            ClientConfiguration config = ClientConfiguration.builder()
                    .uri(BASE_URI)
                    .connectTimeout(CONNECT)
                    .readTimeout(READ)
                    .serializer(new TestJsonSerializer())
                    .logging(custom)
                    .build();

            assertEquals(custom, config.logging());
        }
    }
}
