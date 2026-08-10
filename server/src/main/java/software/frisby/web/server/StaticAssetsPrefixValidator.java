package software.frisby.web.server;

import java.util.List;

/**
 * Validates that a list of {@link StaticAssetsConfiguration} instances have
 * non-conflicting URL prefixes.
 *
 * <p>Two prefixes conflict when one is an ancestor path of the other — meaning
 * every request that would be routed to the more-specific prefix would also be
 * matched by the broader one, causing ambiguous routing.  The root prefix
 * {@code "/"} conflicts with every other prefix.
 */
final class StaticAssetsPrefixValidator {
    private static final String DUPLICATE_MESSAGE =
            "The 'staticAssets' value is invalid.  The URL prefix '%s' is configured more than once.  Each prefix must be unique.";

    private static final String OVERLAP_MESSAGE =
            "The 'staticAssets' value is invalid.  The URL prefix '%s' conflicts with '%s'.  Prefixes must not overlap.";

    private StaticAssetsPrefixValidator() {
    }

    /**
     * Validates that no two configurations in the list have duplicate or
     * overlapping URL prefixes.
     *
     * @param configurations the list of configurations to validate; must not be
     *                       {@code null}; may be empty
     * @throws IllegalStateException if any two prefixes are identical or one is
     *                               an ancestor path of the other
     */
    static void validate(List<StaticAssetsConfiguration> configurations) {
        for (int i = 0; i < configurations.size(); i++) {
            String a = configurations.get(i).urlPrefix();

            for (int j = i + 1; j < configurations.size(); j++) {
                String b = configurations.get(j).urlPrefix();

                if (a.equals(b)) {
                    throw new IllegalStateException(String.format(DUPLICATE_MESSAGE, a));
                }

                if (isAncestorPathOf(a, b)) {
                    throw new IllegalStateException(String.format(OVERLAP_MESSAGE, a, b));
                }

                if (isAncestorPathOf(b, a)) {
                    throw new IllegalStateException(String.format(OVERLAP_MESSAGE, b, a));
                }
            }
        }
    }

    /**
     * Returns {@code true} if {@code ancestor} is a strict ancestor path of
     * {@code path} — i.e. every request matching {@code path} would also be
     * matched by {@code ancestor}.
     *
     * <p>{@code "/"} is an ancestor of every valid prefix.  For any other
     * {@code ancestor}, {@code path} must start with {@code ancestor + "/"}.
     * Note that this check intentionally excludes the equal case; equality is
     * handled separately as a duplicate.
     */
    private static boolean isAncestorPathOf(String ancestor, String path) {
        if (ancestor.equals("/")) {
            return true;
        }

        return path.startsWith(ancestor + "/");
    }
}

