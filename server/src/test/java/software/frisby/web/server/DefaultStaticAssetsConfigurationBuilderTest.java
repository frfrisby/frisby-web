package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.frisby.core.validation.BlankValueException;
import software.frisby.core.validation.DisallowedValueException;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.NullMapKeyException;
import software.frisby.core.validation.NullMapValueException;
import software.frisby.core.validation.NullValueException;
import software.frisby.core.validation.PatternMismatchException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    // notFoundPage()
    // -------------------------------------------------------------------------

    @Nested
    class NotFoundPage {
        @Test
        void nullPath_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").notFoundPage(null)
            );
        }

        @Test
        void blankPath_throwsBlankValueException() {
            assertThrows(
                    BlankValueException.class,
                    () -> StaticAssetsConfiguration.classpath("/web").notFoundPage("   ")
            );
        }

        @Test
        void validPath_stored() {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.classpath("/web")
                    .notFoundPage("404.html")
                    .build();

            assertEquals(Optional.of("404.html"), config.notFoundPage());
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
            assertTrue(config.notFoundPage().isEmpty());
            assertTrue(config.authFilter().isEmpty());
        }

        @Test
        void filesystem_defaultsCorrect(@TempDir Path tempDir) {
            StaticAssetsConfiguration config = StaticAssetsConfiguration.filesystem(tempDir).build();

            assertEquals("/", config.urlPrefix());
            assertTrue(config.cacheMaxAge().isEmpty());
            assertEquals(Map.of(), config.responseHeaders());
            assertFalse(config.spaFallback());
            assertTrue(config.notFoundPage().isEmpty());
            assertTrue(config.authFilter().isEmpty());
        }
    }
}

