package software.frisby.web.server;

import org.eclipse.jetty.util.resource.FileSystemPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.frisby.web.test.log.LogExpectation;
import software.frisby.web.test.log.SystemLogVerifier;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression test for a Jetty {@code "Leaked Mount"} warning previously logged for real-world
 * usage of {@link StaticAssetsConfiguration#classpath(String)} combined with
 * {@link StaticAssetsConfigurationBuilder#spaFallback()} when the application is packaged and
 * run as a single executable jar (e.g. a Spring-Boot-style fat jar or a {@code maven-shade-plugin}
 * uber-jar).
 *
 * <p><strong>Why this never showed up during {@code mvn test}:</strong> {@code
 * StaticAssetsResourceResolver.resolveBaseResource()} resolves the configured classpath path via
 * {@code ClassLoader.getResources(...)}. During normal Maven test runs, the context classloader
 * serves {@code server/src/test/resources} as an <em>exploded directory</em> on disk (a {@code
 * file:} URI), so Jetty's {@code ResourceFactory} never needs to mount a jar {@code FileSystem} —
 * mounting (and the warning that used to come with it) only happens when the resolved resource
 * actually lives inside a jar.
 *
 * <p>This test closes that gap by building a tiny jar containing {@code static/index.html} at
 * test time and pointing the thread context classloader at a {@link URLClassLoader} over that jar
 * for the duration of {@link Server#start()} / {@link Server#stop()}. It asserts the fix holds on
 * both fronts:
 * <ol>
 *   <li>No {@code WARNING}-level {@code "Leaked Mount[...]"} message is logged. (Previously,
 *       {@code StaticAssetsResourceResolver} resolved classpath/jar resources through the
 *       JVM-wide {@code ResourceFactory.root()}, which Jetty documents as having "no lifecycle
 *       that can clean up the mount" and unconditionally logs as a leak the instant a jar is
 *       mounted — see {@code ResourceFactoryInternals.ROOT.onMounted()} in jetty-util.)</li>
 *   <li>The underlying {@link FileSystemPool.Mount} for that jar is released (no longer present
 *       in {@link FileSystemPool#INSTANCE}) once {@link Server#stop()} returns — proving the jar
 *       {@code FileSystem} is a real resource tied to this server's own lifecycle, not the JVM's.
 *       {@code StaticAssetsResourceResolver} now resolves through a {@code
 *       ResourceFactory.lifecycle()} instance owned by {@code DefaultServer} and stopped in
 *       {@code DefaultServer.stop()} — see {@code DefaultServer.staticAssetsResourceFactory}.</li>
 * </ol>
 */
class StaticAssetsJarResourceLeakTest {
    /**
     * The logger name under which Jetty would log this warning: {@code
     * ResourceFactoryInternals.ROOT}'s {@code onMounted()} override calls an unqualified {@code
     * LOG}, which resolves to the field declared on the {@code ResourceFactory} interface itself
     * ({@code Logger LOG = LoggerFactory.getLogger(ResourceFactory.class);}) rather than {@code
     * ResourceFactoryInternals}'s own private {@code LOG} field -- Java field lookup favors an
     * inherited interface field over an enclosing (but non-inherited) class field of the same
     * name. This matches the logger suffix in the original report exactly: {@code
     * util.resource.ResourceFactory:93}.
     */
    private static final String JAR_RESOURCE_FACTORY_LOGGER =
            "org.eclipse.jetty.util.resource.ResourceFactory";

    /**
     * Builds a jar at {@code jarPath} containing a single {@code static/index.html} entry (plus
     * its parent directory entry), mimicking a packaged SPA build output bundled onto the
     * classpath of an executable jar.
     */
    private static void buildStaticAssetsJar(Path jarPath) throws Exception {
        try (OutputStream out = Files.newOutputStream(jarPath);
             JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("static/"));
            jar.closeEntry();

            jar.putNextEntry(new JarEntry("static/index.html"));
            jar.write("<html>jar-packaged-spa-shell</html>".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private static Server buildServerWithJarBackedSpaFallback() {
        return Server.builder()
                .configuration(
                        ServerConfiguration.builder()
                                .port(0)
                                .serializer(new TestJsonSerializer())
                                .build()
                )
                .resources(new PingResource())
                .components(TestLogging.forClass(StaticAssetsJarResourceLeakTest.class))
                .staticAssets(
                        StaticAssetsConfiguration.classpath("/static")
                                .spaFallback()
                                .build()
                )
                .build();
    }

    @Test
    void classpathAssetsPackagedInJar_startAndStopNeverLeakTheMount(@TempDir Path tempDir) throws Exception {
        Path jarPath = tempDir.resolve("packaged-static-assets.jar");
        buildStaticAssetsJar(jarPath);

        URL jarUrl = jarPath.toUri().toURL();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Server server = null;

        try (URLClassLoader jarClassLoader = new URLClassLoader(new URL[] {jarUrl}, null);
             SystemLogVerifier verifier = SystemLogVerifier.builder()
                     .expect(LogExpectation.builder()
                             .logger(JAR_RESOURCE_FACTORY_LOGGER)
                             .level(System.Logger.Level.WARNING)
                             .predicate(e -> e.message().contains("Leaked") && e.message().contains("static"))
                             .build()
                     )
                     .build()) {
            Thread.currentThread().setContextClassLoader(jarClassLoader);

            server = buildServerWithJarBackedSpaFallback();
            server.start();
            server.stop();

            // 1. No "Leaked Mount" WARNING was logged for the jar-backed '/static' classpath
            // resource -- StaticAssetsResourceResolver no longer resolves through the JVM-wide
            // ResourceFactory.root().
            assertFalse(
                    leakedMountWarningWasLogged(verifier),
                    "Did not expect a 'Leaked Mount' warning for the jar-backed '/static' classpath "
                            + "resource -- StaticAssetsResourceResolver should resolve through a "
                            + "server-lifecycle-scoped ResourceFactory, not ResourceFactory.root()."
            );

            // 2. The jar's FileSystem mount was actually released on stop(), not merely
            // un-warned-about -- proving this is a real, server-lifecycle-scoped resource.
            boolean mountStillPresent = FileSystemPool.INSTANCE.mounts().stream()
                    .anyMatch(mount -> mount.toString().contains(jarPath.getFileName().toString()));

            assertFalse(
                    mountStillPresent,
                    "Expected the jar's FileSystemPool mount to be released after server.stop() -- "
                            + "DefaultServer.staticAssetsResourceFactory should be stopped there, closing "
                            + "every mount it made while resolving StaticAssetsConfiguration sources."
            );
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);

            if (null != server && server.isRunning()) {
                server.stop();
            }
        }
    }

    /**
     * Sanity-check counterpart to the test above: confirms the warning does <strong>not</strong>
     * occur when the same classpath path resolves to an exploded directory (the normal case for
     * {@code mvn test}), which is why the original leak never showed up in this module's existing
     * {@link ServerStaticAssetsTest} suite.
     */
    @Test
    void classpathAssetsAsExplodedDirectory_startDoesNotLogLeakedMountWarning() {
        try (SystemLogVerifier verifier = SystemLogVerifier.builder()
                .expect(LogExpectation.builder()
                        .logger(JAR_RESOURCE_FACTORY_LOGGER)
                        .level(System.Logger.Level.WARNING)
                        .predicate(e -> e.message().contains("Leaked"))
                        .build()
                )
                .build()) {
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(StaticAssetsJarResourceLeakTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath("/static-test-assets")
                                    .spaFallback()
                                    .build()
                    )
                    .build();

            try {
                server.start();
            } finally {
                server.stop();
            }

            // No matching WARNING should ever have been recorded for this exploded-directory case
            // -- the "Leaked" expectation above must still be unsatisfied with a zero timeout.
            assertFalse(
                    leakedMountWarningWasLogged(verifier),
                    "Did not expect a 'Leaked Mount' warning when the classpath resource resolves to "
                            + "an exploded directory rather than a jar."
            );
        }
    }

    /**
     * Returns {@code true} if the sole registered {@code "Leaked"} expectation on {@code verifier}
     * was satisfied. {@link SystemLogVerifier#assertExpectations()} throws {@link AssertionError}
     * immediately (zero timeout) when an expectation was never matched, which is the expected,
     * desired outcome in both tests above -- so that outcome is translated to {@code false} here
     * rather than re-thrown.
     */
    private static boolean leakedMountWarningWasLogged(SystemLogVerifier verifier) {
        try {
            verifier.assertExpectations();
            return true;
        } catch (AssertionError notLogged) {
            return false;
        }
    }
}




