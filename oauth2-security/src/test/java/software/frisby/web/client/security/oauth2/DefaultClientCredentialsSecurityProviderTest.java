package software.frisby.web.client.security.oauth2;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.AbortedException;
import software.frisby.web.client.exception.ConnectTimeoutException;
import software.frisby.web.client.exception.ReadTimeoutException;
import software.frisby.web.client.security.RequestContext;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultClientCredentialsSecurityProviderTest {
    private static final String TOKEN_PATH = "/oauth/token";
    private static final String TOKEN_RESPONSE =
            "{\"access_token\":\"test-bearer-token\",\"expires_in\":3600}";
    private static final String TOKEN_RESPONSE_EXPIRED =
            "{\"access_token\":\"expired-token\",\"expires_in\":5}";
    private static final String BEARER_TOKEN = "test-bearer-token";

    private WireMockServer wireMock;
    private URI tokenEndpoint;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        tokenEndpoint = URI.create("http://localhost:" + wireMock.port() + TOKEN_PATH);
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    private ClientCredentialsSecurityProvider provider() {
        return ClientCredentialsSecurityProvider.builder()
                .tokenEndpoint(tokenEndpoint)
                .credentials("test-client", "test-secret")
                .serializer(new TestJacksonSerializer())
                .build();
    }

    private static CapturingRequestContext secure(ClientCredentialsSecurityProvider provider) {
        CapturingRequestContext ctx = new CapturingRequestContext();

        provider.secure(ctx);
        return ctx;
    }

    private void stubTokenEndpoint(String responseBody) {
        wireMock.stubFor(
                post(urlEqualTo(TOKEN_PATH))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(responseBody)
                        )
        );
    }

    // -------------------------------------------------------------------------
    // Successful token fetch
    // -------------------------------------------------------------------------

    @Nested
    class SuccessfulTokenFetch {
        @Test
        void secure_setsBearerAuthorizationHeader() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            CapturingRequestContext ctx = secure(provider());

            assertEquals("Bearer " + BEARER_TOKEN, ctx.header("Authorization"));
        }

        @Test
        void secure_fetchesTokenFromEndpoint() {            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(provider());

            wireMock.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        }

        @Test
        void requestBody_containsGrantType() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(provider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(containing("grant_type=client_credentials"))
            );
        }

        @Test
        void requestBody_containsClientId() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(provider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(containing("client_id=test-client"))
            );
        }

        @Test
        void requestBody_containsClientSecret() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(provider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(containing("client_secret=test-secret"))
            );
        }

        @Test
        void requestHeader_contentTypeIsFormUrlEncoded() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(provider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            );
        }

        @Test
        void withCustomSslContext_sslContextPassedToHttpClient() throws NoSuchAlgorithmException {
            // SSLContext is set on the underlying HttpClient builder; the plain HTTP
            // WireMock server is still reachable — this covers the null != sslContext branch.
            stubTokenEndpoint(TOKEN_RESPONSE);

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .sslContext(SSLContext.getDefault())
                    .build();

            CapturingRequestContext ctx = secure(provider);

            assertEquals("Bearer " + BEARER_TOKEN, ctx.header("Authorization"));
        }
    }

    // -------------------------------------------------------------------------
    // Token caching
    // -------------------------------------------------------------------------

    @Nested
    class TokenCaching {
        @Test
        void tokenCached_onlyOneFetchForMultipleCalls() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            ClientCredentialsSecurityProvider provider = provider();

            secure(provider);
            secure(provider);
            secure(provider);

            wireMock.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        }

        @Test
        void tokenCached_sameBearerValueOnSubsequentCalls() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            ClientCredentialsSecurityProvider provider = provider();

            assertEquals(
                    secure(provider).header("Authorization"),
                    secure(provider).header("Authorization")
            );
        }
    }

    // -------------------------------------------------------------------------
    // Token expiry — zero/missing expires_in triggers re-fetch every call;
    // expires_in < buffer uses full token lifetime
    // -------------------------------------------------------------------------

    @Nested
    class TokenExpiry {
        @Test
        void expiresInLessThanBuffer_tokenCachedForFullLifetime() {
            // expires_in=5 < buffer=30s → expiresAt = now + 5s → rapid successive calls share
            // the same token; buffer is not applied when it exceeds the token lifetime
            stubTokenEndpoint(TOKEN_RESPONSE_EXPIRED);

            ClientCredentialsSecurityProvider provider = provider();

            secure(provider);
            secure(provider);

            wireMock.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        }

        @Test
        void zeroExpiresIn_tokenRefetchedOnEveryCall() {
            stubTokenEndpoint("{\"access_token\":\"zero-expiry\",\"expires_in\":0}");

            ClientCredentialsSecurityProvider provider = provider();

            secure(provider);
            secure(provider);

            wireMock.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        }

        @Test
        void missingExpiresIn_tokenRefetchedOnEveryCall() {
            stubTokenEndpoint("{\"access_token\":\"no-expiry-token\"}");

            ClientCredentialsSecurityProvider provider = provider();

            secure(provider);
            secure(provider);

            wireMock.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        }
    }

    // -------------------------------------------------------------------------
    // HTTP error responses → TokenEndpointException
    // -------------------------------------------------------------------------

    @Nested
    class HttpErrorResponse {
        @Test
        void http401_throwsTokenEndpointExceptionWithStatus() {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(
                                    aResponse()
                                            .withStatus(401)
                                            .withBody("{\"error\":\"unauthorized_client\"}")
                            )
            );

            TokenEndpointException ex = assertThrows(
                    TokenEndpointException.class,
                    () -> secure(provider())
            );

            assertEquals(401, ex.statusCode());
            assertEquals(tokenEndpoint, ex.tokenEndpoint());
        }

        @Test
        void http401_bodyPreservedInException() {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(
                                    aResponse()
                                            .withStatus(401)
                                            .withBody("{\"error\":\"unauthorized_client\"}")
                            )
            );

            TokenEndpointException ex = assertThrows(
                    TokenEndpointException.class,
                    () -> secure(provider())
            );

            assertTrue(ex.body().isPresent());
            assertTrue(ex.body().get().contains("unauthorized_client"));
        }

        @Test
        void http500_throwsTokenEndpointExceptionWithStatus() {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(
                                    aResponse()
                                            .withStatus(500)
                                            .withBody("{\"error\":\"server_error\"}")
                            )
            );

            TokenEndpointException ex = assertThrows(
                    TokenEndpointException.class,
                    () -> secure(provider())
            );

            assertEquals(500, ex.statusCode());
        }

        @Test
        void http302_redirectNeverPolicy_throwsTokenEndpointExceptionWithRedirectStatus() {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(
                                    aResponse()
                                            .withStatus(302)
                                            .withHeader("Location", "https://auth.example.com/oauth/token")
                            )
            );

            TokenEndpointException ex = assertThrows(
                    TokenEndpointException.class,
                    () -> secure(provider())
            );

            assertEquals(302, ex.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Malformed success response → TokenEndpointException with statusCode=0
    // -------------------------------------------------------------------------

    @Nested
    class MalformedResponse {
        @Test
        void missingAccessToken_throwsTokenEndpointException() {
            stubTokenEndpoint("{\"token_type\":\"bearer\",\"expires_in\":3600}");

            TokenEndpointException ex = assertThrows(
                    TokenEndpointException.class,
                    () -> secure(provider())
            );

            assertEquals(0, ex.statusCode());
        }

        @Test
        void emptyAccessToken_throwsTokenEndpointException() {
            stubTokenEndpoint("{\"access_token\":\"\",\"expires_in\":3600}");

            TokenEndpointException ex = assertThrows(
                    TokenEndpointException.class,
                    () -> secure(provider())
            );

            assertEquals(0, ex.statusCode());
        }

        @Test
        void blankAccessToken_throwsTokenEndpointException() {
            stubTokenEndpoint("{\"access_token\":\"   \",\"expires_in\":3600}");

            TokenEndpointException ex = assertThrows(
                    TokenEndpointException.class,
                    () -> secure(provider())
            );

            assertEquals(0, ex.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Scope in request body
    // -------------------------------------------------------------------------

    @Nested
    class ScopeParameter {
        @Test
        void scope_includedInRequestBodyWhenConfigured() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .scope("read", "write")
                    .build();

            secure(provider);

            // "read write" is URL-encoded to "read+write" by URLEncoder
            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(containing("scope=read+write"))
            );
        }

        @Test
        void scope_omittedFromRequestBodyWhenNotConfigured() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(provider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(not(containing("scope")))
            );
        }

        @Test
        void singleScope_includedVerbatim() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .scope("openid")
                    .build();

            secure(provider);

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(containing("scope=openid"))
            );
        }
    }

    // -------------------------------------------------------------------------
    // Transport exceptions
    // -------------------------------------------------------------------------

    @Nested
    class TransportExceptions {
        @Test
        void connectTimeout_throwsConnectTimeoutException() {
            // TEST-NET-3 (203.0.113.0/24) is documentation-only and must not be routed
            URI nonRoutableEndpoint = URI.create("http://203.0.113.0:81/oauth/token");

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(nonRoutableEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .connectTimeout(Duration.ofMillis(200))
                    .build();

            assertThrows(
                    ConnectTimeoutException.class,
                    () -> secure(provider)
            );
        }

        @Test
        void connectRefused_nothingListening_throwsConnectException() throws IOException {
            int port;

            try (ServerSocket s = new ServerSocket(0)) {
                port = s.getLocalPort();
            }

            URI refusedEndpoint = URI.create("http://localhost:" + port + "/oauth/token");

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(refusedEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .build();

            assertThrows(
                    software.frisby.web.client.exception.ConnectException.class,
                    () -> secure(provider)
            );
        }

        @Test
        void readTimeout_throwsReadTimeoutException() {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(
                                    aResponse()
                                            .withFixedDelay(2_000)
                                            .withStatus(200)
                                            .withBody(TOKEN_RESPONSE)
                            )
            );

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .requestTimeout(Duration.ofMillis(300))
                    .build();

            assertThrows(
                    ReadTimeoutException.class,
                    () -> secure(provider)
            );
        }

        @Test
        void connectionResetByPeer_throwsAbortedException() {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
            );

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .build();

            assertThrows(
                    AbortedException.class,
                    () -> secure(provider)
            );
        }

        @Test
        void threadInterrupted_throwsAbortedExceptionAndRestoresInterruptFlag() throws InterruptedException {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(
                                    aResponse()
                                            .withFixedDelay(5_000)
                                            .withStatus(200)
                                            .withBody(TOKEN_RESPONSE)
                            )
            );

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .requestTimeout(Duration.ofSeconds(10))
                    .build();

            AtomicReference<Throwable> thrown = new AtomicReference<>();
            AtomicReference<Boolean> interruptFlagRestored = new AtomicReference<>(false);

            Thread thread = new Thread(() -> {
                try {
                    secure(provider);
                } catch (Throwable ex) {
                    thrown.set(ex);
                    interruptFlagRestored.set(Thread.currentThread().isInterrupted());
                }
            });

            thread.start();
            Thread.sleep(300);
            thread.interrupt();
            thread.join(3_000);

            assertInstanceOf(AbortedException.class, thrown.get());
            assertTrue(interruptFlagRestored.get(), "Interrupt flag must be restored after InterruptedException");
        }
    }

    // -------------------------------------------------------------------------
    // TokenEventListener callbacks
    // -------------------------------------------------------------------------

    @Nested
    class EventListener {
        @Test
        void onTokenFetched_calledOnSuccess() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            AtomicInteger fetchCount = new AtomicInteger(0);
            TokenEventListener listener = new TokenEventListener() {
                @Override
                public void onTokenFetched(Duration latency) {
                    fetchCount.incrementAndGet();
                }

                @Override
                public void onTokenFetchFailed(Duration latency, Throwable cause) {
                }
            };

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .eventListener(listener)
                    .build();

            secure(provider);

            assertEquals(1, fetchCount.get());
        }

        @Test
        void onTokenFetchFailed_calledOnHttpError() {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(aResponse().withStatus(401))
            );

            AtomicInteger failCount = new AtomicInteger(0);
            TokenEventListener listener = new TokenEventListener() {
                @Override
                public void onTokenFetched(Duration latency) {
                }

                @Override
                public void onTokenFetchFailed(Duration latency, Throwable cause) {
                    failCount.incrementAndGet();
                }
            };

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .eventListener(listener)
                    .build();

            assertThrows(TokenEndpointException.class, () -> secure(provider));

            assertEquals(1, failCount.get());
        }

        @Test
        void listenerException_swallowed_operationStillSucceeds() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            TokenEventListener explosiveListener = new TokenEventListener() {
                @Override
                public void onTokenFetched(Duration latency) {
                    throw new RuntimeException("Listener exploded!");
                }

                @Override
                public void onTokenFetchFailed(Duration latency, Throwable cause) {
                }
            };

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .eventListener(explosiveListener)
                    .build();

            // The listener exception must not propagate to the caller
            CapturingRequestContext ctx = secure(provider);

            assertEquals("Bearer " + BEARER_TOKEN, ctx.header("Authorization"));
        }

        @Test
        void listenerFailedCallback_exception_swallowed_exceptionStillPropagates() {
            wireMock.stubFor(
                    post(urlEqualTo(TOKEN_PATH))
                            .willReturn(aResponse().withStatus(500))
            );

            TokenEventListener explosiveListener = new TokenEventListener() {
                @Override
                public void onTokenFetched(Duration latency) {
                }

                @Override
                public void onTokenFetchFailed(Duration latency, Throwable cause) {
                    throw new RuntimeException("Failed listener exploded!");
                }
            };

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .eventListener(explosiveListener)
                    .build();

            // The original TokenEndpointException must still propagate
            assertThrows(TokenEndpointException.class, () -> secure(provider));
        }
    }

    // -------------------------------------------------------------------------
    // Basic Auth — client_secret_basic
    // -------------------------------------------------------------------------

    @Nested
    class BasicAuth {
        private static final String EXPECTED_BASIC_HEADER =
                "Basic " + Base64.getEncoder().encodeToString(
                        "test-client:test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        private ClientCredentialsSecurityProvider basicAuthProvider() {
            return ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .basicAuth()
                    .build();
        }

        @Test
        void basicAuth_credentialsInAuthorizationHeader() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(basicAuthProvider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withHeader("Authorization", equalTo(EXPECTED_BASIC_HEADER))
            );
        }

        @Test
        void basicAuth_clientIdNotInRequestBody() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(basicAuthProvider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(not(containing("client_id")))
            );
        }

        @Test
        void basicAuth_clientSecretNotInRequestBody() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(basicAuthProvider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(not(containing("client_secret")))
            );
        }

        @Test
        void basicAuth_grantTypeStillInRequestBody() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            secure(basicAuthProvider());

            wireMock.verify(
                    postRequestedFor(urlEqualTo(TOKEN_PATH))
                            .withRequestBody(containing("grant_type=client_credentials"))
            );
        }

        @Test
        void basicAuth_setsBearerTokenOnOutboundRequest() {
            stubTokenEndpoint(TOKEN_RESPONSE);

            CapturingRequestContext ctx = secure(basicAuthProvider());

            assertEquals("Bearer " + BEARER_TOKEN, ctx.header("Authorization"));
        }
    }

    // -------------------------------------------------------------------------
    // Expiry buffer — configurable token refresh window
    // -------------------------------------------------------------------------

    @Nested
    class ExpiryBuffer {
        @Test
        void customExpiryBuffer_tokenWithinBufferCachedForFullLifetime() {
            // expires_in=3, buffer=5 → 3 < 5 → expiresAt = now + 3s → rapid calls share the token
            stubTokenEndpoint("{\"access_token\":\"short-lived-token\",\"expires_in\":3}");

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .expiryBuffer(Duration.ofSeconds(5))
                    .build();

            secure(provider);
            secure(provider);

            wireMock.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        }

        @Test
        void customExpiryBuffer_tokenBeyondBufferIsCached() {
            // expires_in=100, expiryBuffer=5 → 100 > 5 → token cached
            stubTokenEndpoint("{\"access_token\":\"long-lived-token\",\"expires_in\":100}");

            ClientCredentialsSecurityProvider provider = ClientCredentialsSecurityProvider.builder()
                    .tokenEndpoint(tokenEndpoint)
                    .credentials("test-client", "test-secret")
                    .serializer(new TestJacksonSerializer())
                    .expiryBuffer(Duration.ofSeconds(5))
                    .build();

            secure(provider);
            secure(provider);

            wireMock.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        }
    }

    // -------------------------------------------------------------------------
    // Test helper
    // -------------------------------------------------------------------------

    private static final class CapturingRequestContext implements RequestContext {
        private final Map<String, String> headers = new LinkedHashMap<>();

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void addCookie(HttpCookie cookie) {
        }

        String header(String name) {
            return headers.get(name);
        }
    }
}

