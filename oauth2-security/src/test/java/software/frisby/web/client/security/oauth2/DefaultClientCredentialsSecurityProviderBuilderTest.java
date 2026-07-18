package software.frisby.web.client.security.oauth2;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.NullValueException;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultClientCredentialsSecurityProviderBuilderTest {
    private static final URI TOKEN_ENDPOINT = URI.create("https://auth.example.com/oauth/token");

    // -------------------------------------------------------------------------
    // tokenEndpoint validation
    // -------------------------------------------------------------------------

    @Nested
    class TokenEndpoint {
        @Test
        void nullTokenEndpoint_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder().tokenEndpoint(null)
            );
        }

        @Test
        void missingTokenEndpoint_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .credentials("client-id", "client-secret")
                            .serializer(new TestJacksonSerializer())
                            .build()
            );
        }
    }

    // -------------------------------------------------------------------------
    // credentials validation
    // -------------------------------------------------------------------------

    @Nested
    class Credentials {
        @Test
        void nullCredentialsObject_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .credentials(null)
            );
        }

        @Test
        void nullClientId_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .credentials(null, "client-secret")
            );
        }

        @Test
        void nullClientSecret_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .credentials("client-id", null)
            );
        }

        @Test
        void missingCredentials_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .tokenEndpoint(TOKEN_ENDPOINT)
                            .serializer(new TestJacksonSerializer())
                            .build()
            );
        }
    }

    // -------------------------------------------------------------------------
    // serializer validation
    // -------------------------------------------------------------------------

    @Nested
    class Serializer {
        @Test
        void nullSerializer_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder().serializer(null)
            );
        }

        @Test
        void missingSerializer_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .tokenEndpoint(TOKEN_ENDPOINT)
                            .credentials("client-id", "client-secret")
                            .build()
            );
        }
    }

    // -------------------------------------------------------------------------
    // scope validation
    // -------------------------------------------------------------------------

    @Nested
    class Scope {
        @Test
        void nullScopeArray_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder().scope((String[]) null)
            );
        }
    }

    // -------------------------------------------------------------------------
    // optional field setters — null checks
    // -------------------------------------------------------------------------

    @Nested
    class OptionalFields {
        @Test
        void nullConnectTimeout_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder().connectTimeout(null)
            );
        }

        @Test
        void negativeConnectTimeout_throwsRuntimeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .connectTimeout(Duration.ofSeconds(-1))
            );
        }

        @Test
        void zeroConnectTimeout_throwsRuntimeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .connectTimeout(Duration.ZERO)
            );
        }

        @Test
        void nullRequestTimeout_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder().requestTimeout(null)
            );
        }

        @Test
        void negativeRequestTimeout_throwsRuntimeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .requestTimeout(Duration.ofSeconds(-1))
            );
        }

        @Test
        void nullSslContext_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder().sslContext(null)
            );
        }

        @Test
        void nullEventListener_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder().eventListener(null)
            );
        }

        @Test
        void nullExpiryBuffer_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> ClientCredentialsSecurityProvider.builder().expiryBuffer(null)
            );
        }

        @Test
        void negativeExpiryBuffer_throwsRuntimeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .expiryBuffer(Duration.ofSeconds(-1))
            );
        }

        @Test
        void zeroExpiryBuffer_throwsRuntimeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> ClientCredentialsSecurityProvider.builder()
                            .expiryBuffer(Duration.ZERO)
            );
        }
    }

    // -------------------------------------------------------------------------
    // build — fully configured
    // -------------------------------------------------------------------------

    @Nested
    class Build {
        @Test
        void allRequiredFields_buildsSuccessfully() {
            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(TOKEN_ENDPOINT)
                    .credentials("client-id", "client-secret")
                    .serializer(new TestJacksonSerializer())
                    .build();

            // Provider must not be null — runtime behavior is tested in integration tests
            assert provider != null;
        }

        @Test
        void credentialsObject_buildsSuccessfully() {
            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(TOKEN_ENDPOINT)
                    .credentials(ClientCredentials.of("client-id", "client-secret"))
                    .serializer(new TestJacksonSerializer())
                    .build();

            assert provider != null;
        }

        @Test
        void withAllOptionalFields_buildsSuccessfully() {
            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(TOKEN_ENDPOINT)
                    .credentials("client-id", "client-secret")
                    .serializer(new TestJacksonSerializer())
                    .scope("read", "write")
                    .connectTimeout(Duration.ofSeconds(5))
                    .requestTimeout(Duration.ofSeconds(15))
                    .expiryBuffer(Duration.ofSeconds(60))
                    .basicAuth()
                    .eventListener(new NoOpTokenEventListener())
                    .build();

            assert provider != null;
        }
    }

    // -------------------------------------------------------------------------
    // Helper — expose NoOpTokenEventListener for tests in this package
    // -------------------------------------------------------------------------

    private static final class NoOpTokenEventListener implements TokenEventListener {
        @Override
        public void onTokenFetched(java.time.Duration latency) {
        }

        @Override
        public void onTokenFetchFailed(java.time.Duration latency, Throwable cause) {
        }
    }
}

