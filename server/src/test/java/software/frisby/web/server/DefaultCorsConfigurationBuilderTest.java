package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.MissingElementsException;
import software.frisby.core.validation.NullValueException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCorsConfigurationBuilderTest {
    private static final String WILDCARD_WITH_CREDENTIALS_MESSAGE =
            "allowCredentials() cannot be combined with a wildcard origin ('*')";

    // -------------------------------------------------------------------------
    // allowedOrigins
    // -------------------------------------------------------------------------

    @Nested
    class AllowedOrigins {
        @Test
        void noOriginsConfigured_throwsEmptyValueException() {
            assertThrows(
                    MissingElementsException.class,
                    () -> CorsConfiguration.builder()
                            .allowedMethods("GET")
                            .build()
            );
        }

        @Test
        void nullOrigins_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> CorsConfiguration.builder().allowedOrigins((String[]) null)
            );
        }

        @Test
        void blankOrigin_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> CorsConfiguration.builder().allowedOrigins("  ")
            );
        }

        @Test
        void singleOrigin_isApplied() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET")
                    .build();

            assertEquals(List.of("https://app.example.com"), config.allowedOrigins());
        }

        @Test
        void multipleOrigins_areApplied() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com", "https://admin.example.com")
                    .allowedMethods("GET")
                    .build();

            assertTrue(config.allowedOrigins().contains("https://app.example.com"));
            assertTrue(config.allowedOrigins().contains("https://admin.example.com"));
        }

        @Test
        void wildcardOrigin_isApplied() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("*")
                    .allowedMethods("GET")
                    .build();

            assertTrue(config.allowedOrigins().contains("*"));
        }

        @Test
        void multipleCallsToAllowedOrigins_areAdditive() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedOrigins("https://admin.example.com")
                    .allowedMethods("GET")
                    .build();

            assertEquals(2, config.allowedOrigins().size());
        }
    }

    // -------------------------------------------------------------------------
    // allowedMethods
    // -------------------------------------------------------------------------

    @Nested
    class AllowedMethods {
        @Test
        void noMethodsConfigured_throwsEmptyValueException() {
            assertThrows(
                    MissingElementsException.class,
                    () -> CorsConfiguration.builder()
                            .allowedOrigins("https://app.example.com")
                            .build()
            );
        }

        @Test
        void nullMethods_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> CorsConfiguration.builder().allowedMethods((String[]) null)
            );
        }

        @Test
        void blankMethod_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> CorsConfiguration.builder().allowedMethods("  ")
            );
        }

        @Test
        void multipleMethods_areApplied() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .build();

            assertTrue(config.allowedMethods().contains("GET"));
            assertTrue(config.allowedMethods().contains("POST"));
            assertTrue(config.allowedMethods().contains("PUT"));
            assertTrue(config.allowedMethods().contains("DELETE"));
        }

        @Test
        void multipleCallsToAllowedMethods_areAdditive() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET")
                    .allowedMethods("POST")
                    .build();

            assertEquals(2, config.allowedMethods().size());
        }
    }

    // -------------------------------------------------------------------------
    // allowedHeaders
    // -------------------------------------------------------------------------

    @Nested
    class AllowedHeadersTests {
        @Test
        void noHeadersConfigured_returnsEcho() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET")
                    .build();

            assertInstanceOf(AllowedHeaders.Echo.class, config.allowedHeaders());
        }

        @Test
        void nullHeaders_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> CorsConfiguration.builder().allowedHeaders((String[]) null)
            );
        }

        @Test
        void blankHeader_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> CorsConfiguration.builder().allowedHeaders("  ")
            );
        }

        @Test
        void configuredHeaders_areApplied() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET")
                    .allowedHeaders("Authorization", "Content-Type")
                    .build();

            AllowedHeaders.Explicit explicit = assertInstanceOf(AllowedHeaders.Explicit.class, config.allowedHeaders());
            assertTrue(explicit.headers().contains("Authorization"));
            assertTrue(explicit.headers().contains("Content-Type"));
        }

        @Test
        void multipleCallsToAllowedHeaders_areAdditive() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET")
                    .allowedHeaders("Authorization")
                    .allowedHeaders("Content-Type")
                    .build();

            AllowedHeaders.Explicit explicit = assertInstanceOf(AllowedHeaders.Explicit.class, config.allowedHeaders());
            assertEquals(2, explicit.headers().size());
        }
    }

    // -------------------------------------------------------------------------
    // allowCredentials
    // -------------------------------------------------------------------------

    @Nested
    class AllowCredentials {
        @Test
        void allowCredentials_defaultsToFalse() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET")
                    .build();

            assertFalse(config.allowCredentials());
        }

        @Test
        void allowCredentials_canBeEnabled() {
            CorsConfiguration config = CorsConfiguration.builder()
                    .allowedOrigins("https://app.example.com")
                    .allowedMethods("GET")
                    .allowCredentials()
                    .build();

            assertTrue(config.allowCredentials());
        }

        @Test
        void allowCredentialsWithWildcardOrigin_throwsIllegalStateException() {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> CorsConfiguration.builder()
                            .allowedOrigins("*")
                            .allowedMethods("GET")
                            .allowCredentials()
                            .build()
            );

            assertTrue(ex.getMessage().contains(WILDCARD_WITH_CREDENTIALS_MESSAGE));
        }
    }
}




