package software.frisby.web.server;

import org.eclipse.jetty.util.resource.Resource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for package-private static helper methods on {@link SpaFallbackInflector} that
 * cannot be exercised through the full integration test stack without unrealistic
 * infrastructure.
 */
class SpaFallbackInflectorTest {

    private static Method resolveMethod(String name, Class<?>... paramTypes) {
        try {
            Method m = SpaFallbackInflector.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find SpaFallbackInflector." + name, e);
        }
    }

    /**
     * Tests for the {@code hasFileExtension} private static utility.
     * <p>
     * The slash-present path (e.g. {@code /missing.png}) is exercised by the SPA fallback
     * integration tests in {@link ServerStaticAssetsTest}. The slash-absent branch
     * ({@code lastSlash < 0}) is defensive dead code — every path this method is called
     * with is built as {@code PATH_SEPARATOR + context.getUriInfo().getPath(true)} and so
     * always starts with {@code '/'} — so it can only be reached via a direct call with a
     * slash-free string, the same approach used for the identical method on
     * {@code StaticHandler} in {@code StaticHandlerTest}.
     */
    @Nested
    class HasFileExtension {
        private static final Method METHOD = resolveMethod("hasFileExtension", String.class);

        private static boolean invoke(String path) {
            try {
                return (boolean) METHOD.invoke(null, path);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void noSlash_withExtension_returnsTrue() {
            // lastSlash = -1 → lastSegment = entire path → "file.txt" has extension
            assertTrue(invoke("file.txt"));
        }

        @Test
        void noSlash_noExtension_returnsFalse() {
            assertFalse(invoke("route"));
        }

        @Test
        void noSlash_trailingDot_returnsFalse() {
            // lastDot > 0 is true (dot is present and not first char),
            // but lastDot == lastSegment.length() - 1 (dot is last char) → false.
            assertFalse(invoke("file."));
        }
    }

    /**
     * Tests for the {@code readResourceBytes} package-private static utility.
     * <p>
     * The successful-read path is exercised by every integration test that serves an SPA
     * fallback response in {@link ServerStaticAssetsTest}. The {@code catch (IOException)}
     * block — which rethrows as {@link UncheckedIOException} rather than swallowing the
     * failure to a {@code 404} — cannot be triggered through a live server, since every
     * resource reachable there has already passed a readability check moments earlier.
     * The method is package-private (not private) specifically so this test can call it
     * directly with a {@link Resource} test-double whose {@code newInputStream()} throws,
     * rather than needing reflection.
     */
    @Nested
    class ReadResourceBytes {
        /**
         * Minimal concrete {@link Resource} whose {@link #newInputStream()} always throws
         * {@link IOException} — simulates a file that passed the startup/defense-in-depth
         * readability check but then fails to actually open (e.g. permissions changed,
         * disk error) by the time it is read.
         */
        private static final class ThrowingInputStreamResource extends Resource {
            @Override
            public Path getPath() {
                return null;
            }

            @Override
            public boolean isDirectory() {
                return false;
            }

            @Override
            public boolean isReadable() {
                return true;
            }

            @Override
            public URI getURI() {
                return null;
            }

            @Override
            public String getName() {
                return "";
            }

            @Override
            public String getFileName() {
                return "";
            }

            @Override
            public InputStream newInputStream() throws IOException {
                throw new IOException("simulated read failure");
            }

            @Override
            public Resource resolve(String subUriPath) {
                return this;
            }

            @Override
            public Iterator<Resource> iterator() {
                return Collections.emptyIterator();
            }

            @Override
            public String toString() {
                return "throwing-resource";
            }
        }

        @Test
        void ioExceptionOnRead_throwsUncheckedIOException() {
            Resource resource = new ThrowingInputStreamResource();

            UncheckedIOException thrown = assertThrows(
                    UncheckedIOException.class,
                    () -> SpaFallbackInflector.readResourceBytes(resource)
            );

            assertEquals("Failed to read 'throwing-resource'.", thrown.getMessage());
            assertInstanceOf(IOException.class, thrown.getCause());
            assertEquals("simulated read failure", thrown.getCause().getMessage());
        }
    }

    /**
     * Tests for the {@code cacheControlValue} package-private static utility.
     * <p>
     * The positive-duration branch is exercised by
     * {@code SpaFallbackResponseParity.fallbackResponse_includesCacheControlHeader} in
     * {@link ServerStaticAssetsTest}, which configures {@code cacheMaxAge(Duration.ofDays(7))}.
     * No existing test configures a zero {@code cacheMaxAge} on an SPA-fallback-enabled
     * {@code StaticAssetsConfiguration}, so the {@code maxAge.isZero()} branch is only
     * reachable via a direct call.
     */
    @Nested
    class CacheControlValue {
        @Test
        void zeroDuration_returnsMaxAgeZeroNoCache() {
            assertEquals(
                    "max-age=0, no-cache",
                    SpaFallbackInflector.cacheControlValue(Duration.ZERO)
            );
        }

        @Test
        void positiveDuration_returnsMaxAgeSecondsPublic() {
            assertEquals(
                    "max-age=604800, public",
                    SpaFallbackInflector.cacheControlValue(Duration.ofDays(7))
            );
        }
    }
}

