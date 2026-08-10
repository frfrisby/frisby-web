package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticAssetsPrefixValidatorTest {

    // -------------------------------------------------------------------------
    // No conflict cases
    // -------------------------------------------------------------------------

    @Nested
    class NoConflict {
        @Test
        void emptyList_noException() {
            assertDoesNotThrow(() -> StaticAssetsPrefixValidator.validate(List.of()));
        }

        @Test
        void singleConfiguration_noException() {
            assertDoesNotThrow(() -> StaticAssetsPrefixValidator.validate(List.of(
                    config("/ui")
            )));
        }

        @Test
        void twoNonOverlappingPrefixes_noException() {
            assertDoesNotThrow(() -> StaticAssetsPrefixValidator.validate(List.of(
                    config("/ui"),
                    config("/docs")
            )));
        }

        @Test
        void threeNonOverlappingPrefixes_noException() {
            assertDoesNotThrow(() -> StaticAssetsPrefixValidator.validate(List.of(
                    config("/ui"),
                    config("/docs"),
                    config("/assets")
            )));
        }

        @Test
        void similarButDistinctPrefixes_noException() {
            // /admin vs /administrator — /administrator does NOT start with /admin/
            assertDoesNotThrow(() -> StaticAssetsPrefixValidator.validate(List.of(
                    config("/admin"),
                    config("/administrator")
            )));
        }
    }

    // -------------------------------------------------------------------------
    // Duplicate prefix
    // -------------------------------------------------------------------------

    @Nested
    class DuplicatePrefix {
        @Test
        void identicalPrefixes_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> StaticAssetsPrefixValidator.validate(List.of(
                            config("/ui"),
                            config("/ui")
                    ))
            );
        }

        @Test
        void duplicateRootPrefix_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> StaticAssetsPrefixValidator.validate(List.of(
                            config("/"),
                            config("/")
                    ))
            );
        }
    }

    // -------------------------------------------------------------------------
    // Overlapping prefixes (ancestor path relationship)
    // -------------------------------------------------------------------------

    @Nested
    class OverlappingPrefix {
        @Test
        void parentBeforeChild_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> StaticAssetsPrefixValidator.validate(List.of(
                            config("/admin"),
                            config("/admin/reports")
                    ))
            );
        }

        @Test
        void childBeforeParent_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> StaticAssetsPrefixValidator.validate(List.of(
                            config("/admin/reports"),
                            config("/admin")
                    ))
            );
        }

        @Test
        void rootWithAnyOtherPrefix_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> StaticAssetsPrefixValidator.validate(List.of(
                            config("/"),
                            config("/docs")
                    ))
            );
        }

        @Test
        void anyOtherPrefixWithRoot_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> StaticAssetsPrefixValidator.validate(List.of(
                            config("/docs"),
                            config("/")
                    ))
            );
        }

        @Test
        void deeplyNestedConflict_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> StaticAssetsPrefixValidator.validate(List.of(
                            config("/a/b"),
                            config("/a/b/c/d")
                    ))
            );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static StaticAssetsConfiguration config(String urlPrefix) {
        return StaticAssetsConfiguration.classpath("/web")
                .urlPrefix(urlPrefix)
                .build();
    }
}

