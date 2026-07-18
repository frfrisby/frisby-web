package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.UriSyntaxException;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UriBuilderTest {
    private static final URI BASE = URI.create("https://api.example.com");
    private static final URI BASE_WITH_PATH = URI.create("https://api.example.com/v1");

    // -------------------------------------------------------------------------
    // canonicalizePath
    // -------------------------------------------------------------------------

    @Nested
    class CanonicalizePath {
        @Test
        void nullPath_returnsEmpty() {
            assertEquals("", UriBuilder.canonicalizePath(null));
        }

        @Test
        void blankPath_returnsEmpty() {
            assertEquals("", UriBuilder.canonicalizePath("   "));
        }

        @Test
        void pathWithoutLeadingSlash_addLeadingSlash() {
            assertEquals("/orders", UriBuilder.canonicalizePath("orders"));
        }

        @Test
        void pathWithTrailingSlash_removesTrailingSlash() {
            assertEquals("/orders", UriBuilder.canonicalizePath("/orders/"));
        }

        @Test
        void pathWithLeadingAndTrailingSlash_normalizes() {
            assertEquals("/orders/123", UriBuilder.canonicalizePath("/orders/123/"));
        }

        @Test
        void alreadyCanonicalPath_isUnchanged() {
            assertEquals("/orders/123", UriBuilder.canonicalizePath("/orders/123"));
        }
    }

    // -------------------------------------------------------------------------
    // substitutePath
    // -------------------------------------------------------------------------

    @Nested
    class SubstitutePath {
        @Test
        void nullPath_returnsNull() {
            assertNull(UriBuilder.substitutePath(null, List.of()));
        }

        @Test
        void blankPath_returnsBlankPathUnchanged() {
            List<PathParameter> params = List.of(PathParameter.of("id", "42"));

            assertEquals("   ", UriBuilder.substitutePath("   ", params));
        }

        @Test
        void nullParameters_returnsPathUnchanged() {
            assertEquals("/orders/{id}", UriBuilder.substitutePath("/orders/{id}", null));
        }

        @Test
        void emptyParameters_returnsPathUnchanged() {
            assertEquals("/orders/{id}", UriBuilder.substitutePath("/orders/{id}", List.of()));
        }

        @Test
        void singleParameter_isSubstituted() {
            List<PathParameter> params = List.of(PathParameter.of("id", "42"));

            assertEquals("/orders/42", UriBuilder.substitutePath("/orders/{id}", params));
        }

        @Test
        void multipleParameters_areAllSubstituted() {
            List<PathParameter> params = List.of(
                    PathParameter.of("userId", "99"),
                    PathParameter.of("orderId", "77")
            );

            assertEquals(
                    "/users/99/orders/77",
                    UriBuilder.substitutePath("/users/{userId}/orders/{orderId}", params)
            );
        }

        @Test
        void parameterValueWithSpecialChars_isPercentEncoded() {
            List<PathParameter> params = List.of(PathParameter.of("name", "John Doe"));

            assertEquals("/persons/John%20Doe", UriBuilder.substitutePath("/persons/{name}", params));
        }

        @Test
        void unknownPlaceholder_isLeftUnsubstituted() {
            List<PathParameter> params = List.of(PathParameter.of("id", "42"));

            assertEquals(
                    "/orders/42/{unknown}",
                    UriBuilder.substitutePath("/orders/{id}/{unknown}", params)
            );
        }

        /**
         * A parameter whose name does not appear in the path template at all must throw
         * immediately rather than silently routing to the wrong endpoint.
         */
        @Test
        void parameterNotInPath_throwsUriSyntaxException() {
            List<PathParameter> params = List.of(PathParameter.of("id", "42"));

            UriSyntaxException ex = assertThrows(
                    UriSyntaxException.class,
                    () -> UriBuilder.substitutePath("/orders", params)
            );

            assertEquals(
                    "The 'path' value is invalid.  The placeholder '{id}' is not present in the template '/orders'.",
                    ex.getMessage()
            );
        }
    }

    // -------------------------------------------------------------------------
    // resolve — full URI assembly
    // -------------------------------------------------------------------------

    @Nested
    class Resolve {
        @Test
        void noPathNoQuery_returnsBaseUri() {
            URI result = UriBuilder.resolve(BASE, null, List.of());

            assertEquals(BASE, result);
        }

        @Test
        void withPath_appendsToBase() {
            URI result = UriBuilder.resolve(BASE, "/orders", List.of());

            assertEquals(URI.create("https://api.example.com/orders"), result);
        }

        @Test
        void withBasePathAndResourcePath_concatenatesBoth() {
            URI result = UriBuilder.resolve(BASE_WITH_PATH, "/orders", List.of());

            assertEquals(URI.create("https://api.example.com/v1/orders"), result);
        }

        @Test
        void withSingleQueryParameter_appendsQueryString() {
            List<java.util.Map.Entry<String, String>> query = List.of(
                    java.util.Map.entry("status", "open")
            );

            URI result = UriBuilder.resolve(BASE, "/orders", query);

            assertEquals(URI.create("https://api.example.com/orders?status=open"), result);
        }

        @Test
        void withMultipleQueryParameters_joinsWithAmpersand() {
            List<java.util.Map.Entry<String, String>> query = List.of(
                    java.util.Map.entry("status", "open"),
                    java.util.Map.entry("page", "2")
            );

            URI result = UriBuilder.resolve(BASE, "/orders", query);

            assertEquals(URI.create("https://api.example.com/orders?status=open&page=2"), result);
        }

        @Test
        void queryParameterWithSpecialChars_isPercentEncoded() {
            List<java.util.Map.Entry<String, String>> query = List.of(
                    java.util.Map.entry("q", "hello world")
            );

            URI result = UriBuilder.resolve(BASE, "/search", query);

            assertEquals(URI.create("https://api.example.com/search?q=hello%20world"), result);
        }

        @Test
        void multivaluedQueryParameter_producesRepeatedKeys() {
            List<java.util.Map.Entry<String, String>> query = List.of(
                    java.util.Map.entry("tag", "java"),
                    java.util.Map.entry("tag", "http")
            );

            URI result = UriBuilder.resolve(BASE, "/posts", query);

            assertEquals(URI.create("https://api.example.com/posts?tag=java&tag=http"), result);
        }

        @Test
        void pathWithSubstitutedParameterAndQuery_assemblesCorrectly() {
            // Substitution is done by RequestState before resolve() is called;
            // this test verifies that resolve() correctly assembles a pre-substituted
            // path together with a query string.
            String substitutedPath = UriBuilder.substitutePath("/users/{userId}", List.of(PathParameter.of("userId", "5")));
            List<java.util.Map.Entry<String, String>> query = List.of(
                    java.util.Map.entry("include", "profile")
            );

            URI result = UriBuilder.resolve(BASE, substitutedPath, query);

            assertEquals(URI.create("https://api.example.com/users/5?include=profile"), result);
        }

        @Test
        void withNullQueryParameters_returnsUriWithoutQuery() {
            URI result = UriBuilder.resolve(BASE, "/orders", null);

            assertEquals(URI.create("https://api.example.com/orders"), result);
        }

        @Test
        void withNonBlankFragmentInBase_appendsFragment() {
            URI base = URI.create("https://api.example.com#section");

            URI result = UriBuilder.resolve(base, "/orders", List.of());

            assertEquals(URI.create("https://api.example.com/orders#section"), result);
        }

        @Test
        void withBlankFragmentInBase_omitsFragment() {
            URI base = URI.create("https://api.example.com#");

            URI result = UriBuilder.resolve(base, "/orders", List.of());

            assertEquals(URI.create("https://api.example.com/orders"), result);
        }

        /**
         * A path containing {@code |} is not percent-encoded by {@code canonicalizePath}
         * and is passed verbatim into the URI string.  Java's {@code URI} constructor
         * rejects {@code |} as an illegal character, causing {@code URISyntaxException}
         * which {@code resolve()} wraps as {@code UriSyntaxException}.
         */
        @Test
        void pathWithIllegalCharacter_throwsUriSyntaxException() {
            URI base = URI.create("http://example.com");

            UriSyntaxException ex = assertThrows(
                    UriSyntaxException.class,
                    () -> UriBuilder.resolve(base, "/path|segment", List.of())
            );

            // Message must include base, path, and query context
            assertTrue(ex.getMessage().contains("example.com"));
            assertTrue(ex.getMessage().contains("/path|segment"));
        }
    }
}
