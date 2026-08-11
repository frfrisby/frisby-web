package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.frisby.core.validation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultStaticAssetsConfigurationBuilderTest {

    // -------------------------------------------------------------------------
    // classpath() factory
    // -------------------------------------------------------------------------

    @Nested
    class ClasspathFactory {
        @Test
        void nullResourcePath_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> StaticAssetsConfiguration.classpath(null)
            );
        }

        @Test
        void blankResourcePath_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> StaticAssetsConfiguration.classpath("   ")
            );
        }

        @Test
        void resourcePathWithoutLeadingSlash_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> StaticAssetsConfiguration.classpath("web")
            );
        }

        @Test
        void validResourcePath_returnsBuilder() {
            StaticAssetsConfigurationBuilder builder = StaticAssetsConfiguration.classpath("/web");

            StaticAssetsConfiguration config = builder.build();

            assertEquals(Optional.of("/web"), config.classpathResourcePath());
            assertEquals(Optional.empty(), config.filesystemDirectory());
        }
    }

    // -------------------------------------------------------------------------
    // filesystem() factory
    // -------------------------------------------------------------------------

    @Nested
    class FilesystemFactory {
        @TempDir
        Path tempDir;

        @Test
        void nullDirectory_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> StaticAssetsConfiguration.filesystem(null)
            );
        }

        @Test
        void nonExistentDirectory_throwsDisallowedValueException() {
            Path nonExistent = tempDir.resolve("does-not-exist");

            assertThrows(
                    DisallowedValueException.class,
                    () -> StaticAssetsConfiguration.filesystem(nonExistent)
            );
        }

        @Test
        void regularFile_throwsDisallowedValueException(@TempDir Path dir) throws IOException {
            Path file = Files.createFile(dir.resolve("notadir.txt"));

            assertThrows(
                    DisallowedValueException.class,
                    () -> StaticAssetsConfiguration.filesystem(file)
            );
        }

        @Test
        void validDirectory_returnsBuilder() {
            StaticAssetsConfigurationBuilder builder = StaticAssetsConfiguration.filesystem(tempDir);

            StaticAssetsConfiguration config = builder.build();

            assertEquals(Optional.of(tempDir), config.filesystemDirectory());
            assertEquals(Optional.empty(), config.classpathResourcePath());
        }
    }

    // -------------------------------------------------------------------------
    // urlPrefix()
    // -------------------------------------------------------------------------

    @Nested
    class UrlPrefix {
        @Test
        void nullPrefix_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").urlPrefix(null)
            );
        }

        @Test
        void blankPrefix_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").urlPrefix("   ")
            );
        }

        @Test
        void prefixWithoutLeadingSlash_throwsPatternMismatchException() {
            assertThrows(
                    PatternMismatchException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").urlPrefix("ui")
            );
        }

        @Test
        void validPrefix_stored() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .urlPrefix("/ui")
                    .build();

            assertEquals("/ui", config.urlPrefix());
        }
    }

    // -------------------------------------------------------------------------
    // cacheMaxAge()
    // -------------------------------------------------------------------------

    @Nested
    class CacheMaxAge {
        @Test
        void nullDuration_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").cacheMaxAge(null)
            );
        }

        @Test
        void negativeDuration_throwsDurationOutsideRangeException() {
            assertThrows(
                    DurationOutsideRangeException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").cacheMaxAge(Duration.ofSeconds(-1))
            );
        }

        @Test
        void zeroDuration_accepted() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .cacheMaxAge(Duration.ZERO)
                    .build();

            assertEquals(Optional.of(Duration.ZERO), config.cacheMaxAge());
        }

        @Test
        void positiveDuration_stored() {
            Duration sevenDays = Duration.ofDays(7);

            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .cacheMaxAge(sevenDays)
                    .build();

            assertEquals(Optional.of(sevenDays), config.cacheMaxAge());
        }
    }

    // -------------------------------------------------------------------------
    // responseHeaders()
    // -------------------------------------------------------------------------

    @Nested
    class ResponseHeaders {
        @Test
        void nullMap_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").responseHeaders(null)
            );
        }

        @Test
        void mapWithNullKey_throwsNullMapKeyException() {
            Map<String, String> mapWithNullKey = new HashMap<>();
            mapWithNullKey.put(null, "value");

            assertThrows(
                    NullMapKeyException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").responseHeaders(mapWithNullKey)
            );
        }

        @Test
        void mapWithNullValue_throwsNullMapValueException() {
            Map<String, String> mapWithNullValue = new HashMap<>();
            mapWithNullValue.put("X-Frame-Options", null);

            assertThrows(
                    NullMapValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").responseHeaders(mapWithNullValue)
            );
        }

        @Test
        void validMap_stored() {
            Map<String, String> headers = Map.of("X-Frame-Options", "DENY");

            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .responseHeaders(headers)
                    .build();

            assertEquals(headers, config.responseHeaders());
        }

        @Test
        void emptyMap_accepted() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .responseHeaders(Map.of())
                    .build();

            assertEquals(Map.of(), config.responseHeaders());
        }

        @Test
        void calledTwice_mapsMerged() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .responseHeaders(Map.of("X-Frame-Options", "DENY", "X-Content-Type-Options", "nosniff"))
                    .responseHeaders(Map.of("X-Frame-Options", "SAMEORIGIN", "Referrer-Policy", "no-referrer"))
                    .build();

            assertEquals("SAMEORIGIN", config.responseHeaders().get("X-Frame-Options"));
            assertEquals("nosniff", config.responseHeaders().get("X-Content-Type-Options"));
            assertEquals("no-referrer", config.responseHeaders().get("Referrer-Policy"));
        }
    }

    // -------------------------------------------------------------------------
    // errorPage()
    // -------------------------------------------------------------------------

    @Nested
    class ErrorPage {
        @Test
        void nullPath_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").errorPage(404, null)
            );
        }

        @Test
        void blankPath_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").errorPage(404, "   ")
            );
        }

        @Test
        void statusCodeTooLow_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").errorPage(200, "200.html")
            );
        }

        @Test
        void statusCodeTooHigh_throwsNumericValueOutsideRangeException() {
            assertThrows(
                    NumericValueOutsideRangeException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").errorPage(600, "600.html")
            );
        }

        @Test
        void boundaryStatus400_accepted() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .errorPage(400, "400.html")
                    .build();

            assertEquals("400.html", config.errorPages().get(400));
        }

        @Test
        void boundaryStatus599_accepted() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .errorPage(599, "599.html")
                    .build();

            assertEquals("599.html", config.errorPages().get(599));
        }

        @Test
        void validEntry_stored() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .errorPage(404, "404.html")
                    .build();

            assertEquals("404.html", config.errorPages().get(404));
        }

        @Test
        void multipleStatuses_allStored() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .errorPage(404, "404.html")
                    .errorPage(500, "500.html")
                    .build();

            assertEquals("404.html", config.errorPages().get(404));
            assertEquals("500.html", config.errorPages().get(500));
            assertEquals(2, config.errorPages().size());
        }

        @Test
        void duplicateStatusCode_lastValueWins() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .errorPage(404, "first-404.html")
                    .errorPage(404, "second-404.html")
                    .build();

            assertEquals("second-404.html", config.errorPages().get(404));
            assertEquals(1, config.errorPages().size());
        }
    }

    // -------------------------------------------------------------------------
    // authFilter()
    // -------------------------------------------------------------------------

    @Nested
    class AuthFilter {
        @Test
        void nullFilter_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").authFilter(null)
            );
        }

        @Test
        void validFilter_stored() {
            StaticAssetsAuthFilter filter = (req, res) -> true;

            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .authFilter(filter)
                    .build();

            assertTrue(config.authFilter().isPresent());
            assertSame(filter, config.authFilter().get());
        }
    }

    // -------------------------------------------------------------------------
    // Defaults
    // -------------------------------------------------------------------------

    @Nested
    class Defaults {
        @Test
        void classpath_defaultsCorrect() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web").build();

            assertEquals("/", config.urlPrefix());
            assertTrue(config.cacheMaxAge().isEmpty());
            assertEquals(Map.of(), config.responseHeaders());
            assertFalse(config.spaFallback());
            assertFalse(config.preCompressed());
            assertEquals(Map.of(), config.errorPages());
            assertTrue(config.authFilter().isEmpty());
        }

        @Test
        void filesystem_defaultsCorrect(@TempDir Path tempDir) {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.filesystem(tempDir).build();

            assertEquals("/", config.urlPrefix());
            assertTrue(config.cacheMaxAge().isEmpty());
            assertEquals(Map.of(), config.responseHeaders());
            assertFalse(config.spaFallback());
            assertFalse(config.preCompressed());
            assertEquals(Map.of(), config.errorPages());
            assertTrue(config.authFilter().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // spaFallback()
    // -------------------------------------------------------------------------

    @Nested
    class SpaFallback {
        @Test
        void notCalled_defaultsFalse() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web").build();

            assertFalse(config.spaFallback());
        }

        @Test
        void called_storesTrue() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .spaFallback()
                    .build();

            assertTrue(config.spaFallback());
        }
    }

    // -------------------------------------------------------------------------
    // preCompressed()
    // -------------------------------------------------------------------------

    @Nested
    class PreCompressed {
        @Test
        void notCalled_defaultsFalse() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web").build();

            assertFalse(config.preCompressed());
        }

        @Test
        void called_storesTrue() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .preCompressed()
                    .build();

            assertTrue(config.preCompressed());
        }
    }
}
