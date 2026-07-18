package software.frisby.web.server;

import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.MissingElementsException;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.PatternMismatchException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultServerBuilderTest {
    private static final String NO_CONFIGURATION_MESSAGE =
            "The 'configuration' value is invalid.  A ServerConfiguration is required.";
    private static final String NO_RESOURCES_MESSAGE =
            "The 'resources' value is invalid.  At least one JAX-RS resource must be registered.";

    // -------------------------------------------------------------------------
    // configuration(UnaryOperator<ServerConfigurationBuilder>)
    // -------------------------------------------------------------------------

    @Nested
    class Configuration {
        @Test
        void lambdaOverload_isApplied() {
            Server server = Server.builder()
                    .configuration(c -> c
                            .port(0)
                            .serializer(new TestJsonSerializer()))
                    .resources(new PingResource())
                    .build();

            server.start();
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // components(Object...)
    // -------------------------------------------------------------------------

    @Nested
    class ComponentsVarargs {
        @Test
        void nullComponents_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> Server.builder().components((Object[]) null)
            );
        }

        @Test
        void emptyComponents_throwsMissingElementsException() {
            assertThrows(
                    MissingElementsException.class,
                    () -> Server.builder().components(new Object[]{})
            );
        }

        @Test
        void validComponent_isRegistered() {
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(DefaultServerBuilderTest.class))
                    .build();

            server.start();
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // components(List<Object>)
    // -------------------------------------------------------------------------

    @Nested
    class ComponentsList {
        @Test
        void nullList_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> Server.builder().components((List<Object>) null)
            );
        }

        @Test
        void emptyList_throwsMissingElementsException() {
            assertThrows(
                    MissingElementsException.class,
                    () -> Server.builder().components(List.of())
            );
        }

        @Test
        void validList_isRegistered() {
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(List.of(TestLogging.forClass(DefaultServerBuilderTest.class)))
                    .build();

            server.start();
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // healthCheck(String path) — path validation
    // -------------------------------------------------------------------------

    @Nested
    class HealthCheckPath {
        @Test
        void nullPath_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> Server.builder().healthCheck((String) null)
            );
        }

        @Test
        void blankPath_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> Server.builder().healthCheck("   ")
            );
        }

        @Test
        void pathWithNoLeadingSlash_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> Server.builder().healthCheck("readyz")
            );
        }

        @Test
        void pathWithLeadingDoubleSlash_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> Server.builder().healthCheck("//readyz")
            );
        }

        @Test
        void pathWithMidDoubleSlash_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> Server.builder().healthCheck("/a//b")
            );
        }

        @Test
        void pathWithWhitespace_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> Server.builder().healthCheck("/read yz")
            );
        }

        @Test
        void pathWithTrailingSlash_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> Server.builder().healthCheck("/health/")
            );
        }

        @Test
        void pathWithFragmentCharacter_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> Server.builder().healthCheck("/health#anchor")
            );
        }

        @Test
        void pathWithQueryCharacter_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> Server.builder().healthCheck("/health?ready=true")
            );
        }

        @Test
        void simpleAbsolutePath_isAccepted() {
            Server server = Server.builder()
                    .configuration(c -> c
                            .port(0)
                            .serializer(new TestJsonSerializer()))
                    .resources(new PingResource())
                    .healthCheck("/readyz")
                    .build();

            server.start();
            server.stop();
        }

        @Test
        void nestedAbsolutePath_isAccepted() {
            Server server = Server.builder()
                    .configuration(c -> c
                            .port(0)
                            .serializer(new TestJsonSerializer()))
                    .resources(new PingResource())
                    .healthCheck("/api/v1/health")
                    .build();

            server.start();
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // authentication(AuthenticationProvider...)
    // -------------------------------------------------------------------------

    @Nested
    class AuthenticationVarargs {
        @Test
        void nullProviders_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> Server.builder().authentication((AuthenticationProvider[]) null)
            );
        }

        @Test
        void emptyProviders_throwsMissingElementsException() {
            assertThrows(
                    MissingElementsException.class,
                    () -> Server.builder().authentication(new AuthenticationProvider[]{})
            );
        }

        @Test
        void validProvider_isRegistered() {
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .authentication(new AlwaysAcceptProvider())
                    .build();

            server.start();
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // authentication(List<AuthenticationProvider>)
    // -------------------------------------------------------------------------

    @Nested
    class AuthenticationList {
        @Test
        void nullList_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> Server.builder().authentication((List<AuthenticationProvider>) null)
            );
        }

        @Test
        void emptyList_throwsMissingElementsException() {
            assertThrows(
                    MissingElementsException.class,
                    () -> Server.builder().authentication(List.of())
            );
        }

        @Test
        void validList_isRegistered() {
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .authentication(List.of(new AlwaysAcceptProvider()))
                    .build();

            server.start();
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Always accepts and authenticates any request — for builder wiring tests only. */
    private static final class AlwaysAcceptProvider implements AuthenticationProvider {
        private static final Principal PRINCIPAL = () -> "test";

        @Override
        public boolean accepts(ContainerRequestContext context) {
            return true;
        }

        @Override
        public SecurityContext authenticate(ContainerRequestContext context) {
            return ServerSecurityContext.of(PRINCIPAL);
        }
    }

    // -------------------------------------------------------------------------
    // build() validation
    // -------------------------------------------------------------------------

    @Nested
    class Build {
        @Test
        void noConfiguration_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> Server.builder()
                            .resources(new PingResource())
                            .build()
            );

            assertEquals(NO_CONFIGURATION_MESSAGE, ex.getMessage());
        }

        @Test
        void noResources_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> Server.builder()
                            .configuration(
                                    ServerConfiguration.builder()
                                            .port(0)
                                            .serializer(new TestJsonSerializer())
                                            .build()
                            )
                            .build()
            );

            assertEquals(NO_RESOURCES_MESSAGE, ex.getMessage());
        }
    }
}

