package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.MissingElementsException;
import software.frisby.core.validation.NullValueException;
import software.frisby.web.client.security.SecurityProvider;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestStateTest {
    private static final String NULL_PATH_MSG = "The 'path' value is invalid. The value must not be null.";
    private static final String BLANK_PATH_MSG = "The 'path' value is invalid. The value must not contain only whitespace characters.";
    private static final String NULL_PARAMETERS_MSG = "The 'parameters' value is invalid. The value must not be null.";
    private static final String NULL_COOKIE_MSG = "The 'cookie' value is invalid. The value must not be null.";
    private static final String NULL_PROVIDER_MSG = "The 'provider' value is invalid. The value must not be null.";
    private static final String NULL_NAME_MSG = "The 'name' value is invalid. The value must not be null.";
    private static final String BLANK_NAME_MSG = "The 'name' value is invalid. The value must not contain only whitespace characters.";
    private static final String NULL_VALUE_MSG = "The 'value' value is invalid. The value must not be null.";
    private static final String BLANK_VALUE_MSG = "The 'value' value is invalid. The value must not contain only whitespace characters.";
    private static final String NULL_VALUES_MSG = "The 'values' value is invalid. The value must not be null.";
    private static final String EMPTY_VALUES_MSG = "The 'values' value is invalid. The value must not be empty.";

    // -------------------------------------------------------------------------
    // path(String)
    // -------------------------------------------------------------------------

    @Nested
    class PathSingleArg {
        @Test
        void nullPath_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.path(null)
            );

            assertEquals(NULL_PATH_MSG, ex.getMessage());
        }

        @Test
        void blankPath_throwsBlankValueException() {
            RequestState state = new RequestState(null);

            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> state.path("   ")
            );

            assertEquals(BLANK_PATH_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // path(String, PathParameter...)
    // -------------------------------------------------------------------------

    @Nested
    class PathVarargs {
        @Test
        void nullParametersArray_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.path("/users/{id}", (PathParameter[]) null)
            );

            assertEquals(NULL_PARAMETERS_MSG, ex.getMessage());
        }

        @Test
        void nullPath_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.path(null, PathParameter.of("id", "42"))
            );

            assertEquals(NULL_PATH_MSG, ex.getMessage());
        }

        @Test
        void blankPath_throwsBlankValueException() {
            RequestState state = new RequestState(null);

            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> state.path("   ", PathParameter.of("id", "42"))
            );

            assertEquals(BLANK_PATH_MSG, ex.getMessage());
        }

        @Test
        void emptyParametersArray_setsPath() {
            RequestState state = new RequestState(null);

            state.path("/users/123", new PathParameter[]{});

            URI result = state.resolveUri(URI.create("https://api.example.com"));

            assertEquals("https://api.example.com/users/123", result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // parameter(String, String)
    // -------------------------------------------------------------------------

    @Nested
    class ParameterSingleArg {
        @Test
        void nullName_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.parameter(null, "v")
            );

            assertEquals(NULL_NAME_MSG, ex.getMessage());
        }

        @Test
        void blankName_throwsBlankValueException() {
            RequestState state = new RequestState(null);

            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> state.parameter("   ", "v")
            );

            assertEquals(BLANK_NAME_MSG, ex.getMessage());
        }

        @Test
        void nullValue_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.parameter("q", (String) null)
            );

            assertEquals(NULL_VALUE_MSG, ex.getMessage());
        }

        @Test
        void blankValue_throwsBlankValueException() {
            RequestState state = new RequestState(null);

            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> state.parameter("q", "   ")
            );

            assertEquals(BLANK_VALUE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // parameter(String, String...)
    // -------------------------------------------------------------------------

    @Nested
    class ParameterVarargs {
        @Test
        void nullName_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.parameter(null, "v1", "v2")
            );

            assertEquals(NULL_NAME_MSG, ex.getMessage());
        }

        @Test
        void blankName_throwsBlankValueException() {
            RequestState state = new RequestState(null);

            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> state.parameter("   ", "v1", "v2")
            );

            assertEquals(BLANK_NAME_MSG, ex.getMessage());
        }

        @Test
        void nullValuesArray_throwsNullValueException() {
            RequestState state = new RequestState(null);
            String[] nullValues = null;

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.parameter("q", nullValues)
            );

            assertEquals(NULL_VALUES_MSG, ex.getMessage());
        }

        @Test
        void emptyValuesArray_throwsMissingElementsException() {
            RequestState state = new RequestState(null);
            String[] emptyValues = new String[]{};

            MissingElementsException ex = assertThrows(
                    MissingElementsException.class,
                    () -> state.parameter("q", emptyValues)
            );

            assertEquals(EMPTY_VALUES_MSG, ex.getMessage());
        }
    }
    // -------------------------------------------------------------------------
    // header(String, String)
    // -------------------------------------------------------------------------

    @Nested
    class HeaderSingleArg {
        @Test
        void nullName_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.header(null, "v")
            );

            assertEquals(NULL_NAME_MSG, ex.getMessage());
        }

        @Test
        void blankName_throwsBlankValueException() {
            RequestState state = new RequestState(null);

            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> state.header("   ", "v")
            );

            assertEquals(BLANK_NAME_MSG, ex.getMessage());
        }

        @Test
        void nullValue_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.header("X-Custom", (String) null)
            );

            assertEquals(NULL_VALUE_MSG, ex.getMessage());
        }

        @Test
        void blankValue_throwsBlankValueException() {
            RequestState state = new RequestState(null);

            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> state.header("X-Custom", "   ")
            );

            assertEquals(BLANK_VALUE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // header(String, String...)
    // -------------------------------------------------------------------------

    @Nested
    class HeaderVarargs {
        @Test
        void nullName_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.header(null, "v1", "v2")
            );

            assertEquals(NULL_NAME_MSG, ex.getMessage());
        }

        @Test
        void blankName_throwsBlankValueException() {
            RequestState state = new RequestState(null);

            BlankValueException ex = assertThrows(
                    BlankValueException.class,
                    () -> state.header("   ", "v1", "v2")
            );

            assertEquals(BLANK_NAME_MSG, ex.getMessage());
        }

        @Test
        void nullValuesArray_throwsNullValueException() {
            RequestState state = new RequestState(null);
            String[] nullValues = null;

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.header("X-Custom", nullValues)
            );

            assertEquals(NULL_VALUES_MSG, ex.getMessage());
        }

        @Test
        void emptyValuesArray_throwsMissingElementsException() {
            RequestState state = new RequestState(null);
            String[] emptyValues = new String[]{};

            MissingElementsException ex = assertThrows(
                    MissingElementsException.class,
                    () -> state.header("X-Custom", emptyValues)
            );

            assertEquals(EMPTY_VALUES_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // cookie(HttpCookie)
    // -------------------------------------------------------------------------

    @Nested
    class CookieSingleArg {
        @Test
        void nullCookie_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.cookie(null)
            );

            assertEquals(NULL_COOKIE_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // security(SecurityProvider)
    // -------------------------------------------------------------------------

    @Nested
    class SecuritySingleArg {
        @Test
        void nullProvider_throwsNullValueException() {
            RequestState state = new RequestState(null);

            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> state.security(null)
            );

            assertEquals(NULL_PROVIDER_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // checkNotRestricted — restricted header names
    // -------------------------------------------------------------------------

    @Nested
    class RestrictedHeaders {
        private static final String RESTRICTED_MSG_TEMPLATE =
                "The 'header' value of '%s' is invalid.  This header is managed by the client and cannot be set directly.";

        @Test
        void contentType_singleValue_throwsIllegalArgumentException() {
            RequestState state = new RequestState(null);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> state.header("content-type", "application/json")
            );

            assertEquals(String.format(RESTRICTED_MSG_TEMPLATE, "content-type"), ex.getMessage());
        }

        @Test
        void contentType_caseInsensitive_throwsIllegalArgumentException() {
            RequestState state = new RequestState(null);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> state.header("Content-Type", "application/json")
            );
        }

        @Test
        void accept_varargs_throwsIllegalArgumentException() {
            RequestState state = new RequestState(null);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> state.header("accept", "application/json", "text/plain")
            );

            assertEquals(String.format(RESTRICTED_MSG_TEMPLATE, "accept"), ex.getMessage());
        }

        @Test
        void acceptEncoding_singleValue_throwsIllegalArgumentException() {
            RequestState state = new RequestState(null);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> state.header("accept-encoding", "gzip")
            );
        }

        @Test
        void contentLength_singleValue_throwsIllegalArgumentException() {
            RequestState state = new RequestState(null);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> state.header("content-length", "42")
            );
        }

        @Test
        void transferEncoding_singleValue_throwsIllegalArgumentException() {
            RequestState state = new RequestState(null);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> state.header("transfer-encoding", "chunked")
            );
        }
    }

    // -------------------------------------------------------------------------
    // DefaultRequestContext.addCookie() — via SecurityProvider
    // -------------------------------------------------------------------------

    @Nested
    class RequestContextAddCookie {
        @Test
        void securityProviderAddsCookie_cookieAppearsInRequest() {
            SecurityProvider provider = context -> context.addCookie(new HttpCookie("token", "abc123"));
            RequestState state = new RequestState(provider);

            URI uri = URI.create("http://example.com/test");
            HttpRequest.Builder builder = state.prepareBuilder(
                    uri,
                    "GET",
                    HttpRequest.BodyPublishers.noBody(),
                    false,
                    null,
                    Duration.ofSeconds(30)
            );
            HttpRequest request = builder.build();

            assertEquals(
                    "token=abc123",
                    request.headers().firstValue("Cookie").orElse(null)
            );
        }
    }
}
