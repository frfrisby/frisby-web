package software.frisby.web.server;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import software.frisby.core.validation.FieldGroup;
import software.frisby.core.validation.FieldGroups;

import java.nio.file.Path;

/**
 * Resolves the {@link Resource} backing a {@link StaticAssetsConfiguration}'s asset root.
 *
 * <p>Shared between {@link StaticHandler} (which serves real files from the resource) and
 * {@link DefaultServer} (which, for configurations with {@link StaticAssetsConfiguration#spaFallback()}
 * enabled, needs the same resolved resource to read {@code index.html} from a low-priority JAX-RS
 * fallback resource — see {@code DefaultServer.buildResourceConfig()} for why the fallback is a
 * JAX-RS resource rather than a Jetty {@link org.eclipse.jetty.server.Handler}).
 */
final class StaticAssetsResourceResolver {
    private static final FieldGroup SOURCE_FIELDS =
            FieldGroup.of("classpathResourcePath", "filesystemDirectory");

    /**
     * The single recognized SPA shell / directory-index file name.  Shared between
     * {@link StaticHandler} (welcome-file serving, directory-index resolution) and
     * {@link DefaultServer} (SPA fallback startup validation and request-time serving) so
     * there is exactly one place that decides this convention.  {@code index.htm} is
     * deliberately not supported — every mainstream SPA build tool (Vite, webpack, Rollup,
     * Parcel) emits {@code index.html} exclusively, and supporting both would only add
     * ambiguity with zero real-world benefit.
     */
    static final String INDEX_HTML = "index.html";

    private StaticAssetsResourceResolver() {
    }

    /**
     * Resolves {@code configuration}'s classpath-resource-path or filesystem-directory source
     * into a Jetty {@link Resource}.
     *
     * <p>Does not validate that the result exists or is a directory — callers that need
     * fail-fast startup validation (both current callers do) must check
     * {@code null == resource || !resource.isDirectory()} themselves, since each produces a
     * differently-worded {@link IllegalStateException} tailored to its own call site.
     */
    static Resource resolveBaseResource(StaticAssetsConfiguration configuration) {
        String classpathPath = configuration.classpathResourcePath().orElse(null);
        Path filesystemPath = configuration.filesystemDirectory().orElse(null);

        FieldGroups.onlyOne(
                SOURCE_FIELDS,
                classpathPath,
                filesystemPath
        );

        if (null != classpathPath) {
            return ResourceFactory.root().newClassLoaderResource(classpathPath);
        }

        return ResourceFactory.root().newResource(filesystemPath);
    }
}


