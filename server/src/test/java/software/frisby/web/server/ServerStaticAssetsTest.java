package software.frisby.web.server;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for static asset serving via {@link StaticAssetsConfiguration}.
 * <p>
 * Each nested class spins up a dedicated embedded server on a random port and tears
 * it down in {@code @AfterEach}.  HTTP requests are made with the JDK
 * {@link HttpClient}, configured to follow redirects so that directory-index
 * redirects (e.g. {@code /subdir/} → {@code /subdir/index.html}) are transparent
 * to the test assertions.
 */
class ServerStaticAssetsTest {
    private static final String CLASSPATH_ASSET_ROOT = "/static-test-assets";
    private static final String CLASSPATH_ALT_ASSET_ROOT = "/static-test-assets-alt";

    private static final String INDEX_HTML_CONTENT = "static-test-index";
    private static final String SUBDIR_INDEX_CONTENT = "static-test-subdir";
    private static final String OTHER_HTML_CONTENT = "static-test-other";
    private static final String CUSTOM_404_CONTENT = "static-test-404";
    private static final String CUSTOM_429_CONTENT = "static-test-429";
    private static final String CUSTOM_500_CONTENT = "static-test-500";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // -------------------------------------------------------------------------
    // Content serving
    // -------------------------------------------------------------------------

    @Nested
    class ContentServing {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void indexHtml_returns200WithHtmlContentType() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/html"));
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }

        @Test
        void appJs_returns200WithJavascriptContentType() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/app.js"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("javascript"));
        }

        @Test
        void styleCss_returns200WithCssContentType() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/style.css"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/css"));
        }

        @Test
        void imageSvg_returns200WithSvgContentType() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/image.svg"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("svg"));
        }

        @Test
        void imagePng_returns200WithPngContentType() throws Exception {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/image.png"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("image/png"));
        }
    }

    // -------------------------------------------------------------------------
    // Directory index
    // -------------------------------------------------------------------------

    @Nested
    class DirectoryIndex {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void rootPath_servesIndexHtml() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }

        @Test
        void subdirWithTrailingSlash_servesSubdirIndexHtml() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/subdir/"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(SUBDIR_INDEX_CONTENT));
        }
    }

    // -------------------------------------------------------------------------
    // Filesystem source
    // -------------------------------------------------------------------------

    @Nested
    class FilesystemSource {
        @TempDir
        Path tempDir;

        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            Files.writeString(
                    tempDir.resolve("index.html"),
                    "<!DOCTYPE html><html><body>" + INDEX_HTML_CONTENT + "</body></html>"
            );

            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(tempDir)
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void indexHtml_returns200() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }
    }

    // -------------------------------------------------------------------------
    // SPA fallback
    // -------------------------------------------------------------------------

    @Nested
    class SpaFallback {
        @Nested
        class Enabled {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .spaFallback()
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void extensionlessPath_missingFile_servesIndexHtml() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains(INDEX_HTML_CONTENT));
            }

            @Test
            void pathWithExtension_missingFile_returns404() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/missing.png"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
            }
        }

        @Nested
        class Disabled {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void extensionlessPath_missingFile_returns404() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
            }
        }

        /**
         * SPA fallback is enabled, the asset root has a valid {@code index.html} at startup
         * (so startup validation in {@code DefaultServer.buildResourceConfig()} passes), but
         * the file is deleted before the first request arrives.  Exercises the
         * {@code !Resources.isReadableFile(indexResource)} defense-in-depth guard in
         * {@code SpaFallbackInflector.apply()} — a narrow race, not the common case (which is
         * now caught at startup; see {@code StartupValidation}).
         */
        @Nested
        class IndexHtmlDeletedAfterStartup {
            @TempDir
            Path tmpDir;

            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                Files.writeString(tmpDir.resolve("index.html"), INDEX_HTML_CONTENT);

                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.filesystem(tmpDir)
                                        .spaFallback()
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();

                Files.delete(tmpDir.resolve("index.html"));
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void extensionlessPath_indexHtmlGone_returns404() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
            }
        }

        /**
         * Same race as {@link IndexHtmlDeletedAfterStartup}, but with a custom 404 error page
         * configured — the request must fall through to {@code serveMissingIndexResponse()}'s
         * error-page branch and return the custom body.
         */
        @Nested
        class IndexHtmlDeletedAfterStartupWithErrorPage {
            @TempDir
            Path tmpDir;

            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                Files.writeString(tmpDir.resolve("index.html"), INDEX_HTML_CONTENT);
                Files.writeString(tmpDir.resolve("other.html"), OTHER_HTML_CONTENT);

                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.filesystem(tmpDir)
                                        .spaFallback()
                                        .errorPage(404, "other.html")
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();

                Files.delete(tmpDir.resolve("index.html"));
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void extensionlessPath_indexHtmlGone_servesErrorPage() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
                assertTrue(response.body().contains(OTHER_HTML_CONTENT));
            }
        }

        /**
         * Covers the {@code !Resources.isReadableFile(errorPageResource)} branch of
         * {@code serveMissingIndexResponse()}: {@code errorPage(404, ...)} is configured and
         * valid at startup (required — see {@code StartupValidation}), but the configured file
         * is deleted afterward, alongside {@code index.html} — the same race-condition category
         * as {@link IndexHtmlDeletedAfterStartup}, just extended to the error page as well.  The
         * method must fall through to a plain 404 rather than attempting to read and serve a
         * now-missing file.  {@link IndexHtmlDeletedAfterStartupWithErrorPage}'s error page is
         * never deleted, so it cannot exercise this branch.
         */
        @Nested
        class IndexHtmlDeletedAfterStartupWithUnreadableErrorPage {
            @TempDir
            Path tmpDir;

            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                Files.writeString(tmpDir.resolve("index.html"), INDEX_HTML_CONTENT);
                Files.writeString(tmpDir.resolve("other.html"), OTHER_HTML_CONTENT);

                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.filesystem(tmpDir)
                                        .spaFallback()
                                        .errorPage(404, "other.html")
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();

                // Delete both files after startup validation already passed for each —
                // this is the narrow race serveMissingIndexResponse()'s own Javadoc describes.
                Files.delete(tmpDir.resolve("index.html"));
                Files.delete(tmpDir.resolve("other.html"));
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void extensionlessPath_indexHtmlAndErrorPageBothGone_returnsPlain404() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
            }
        }

        /**
         * Covers the {@code null == contentType} branch of {@code serveMissingIndexResponse()}:
         * the configured error page has an extension {@code MimeTypes.DEFAULTS.getMimeByExtension()}
         * does not recognize, so the method must fall back to {@code "text/html; charset=utf-8"}
         * rather than propagating a {@code null} {@code Content-Type}.  Mirrors
         * {@code ErrorPage.UnknownExtension} for the real {@code StaticHandler} error-page path,
         * but exercised through the SPA-fallback code path instead.
         */
        @Nested
        class IndexHtmlDeletedAfterStartupWithUnknownExtensionErrorPage {
            @TempDir
            Path tmpDir;

            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                Files.writeString(tmpDir.resolve("index.html"), INDEX_HTML_CONTENT);
                Files.writeString(tmpDir.resolve("custom-404.404"), OTHER_HTML_CONTENT);

                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.filesystem(tmpDir)
                                        .spaFallback()
                                        .errorPage(404, "custom-404.404")
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();

                Files.delete(tmpDir.resolve("index.html"));
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void extensionlessPath_indexHtmlGone_usesHtmlFallbackContentType() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
                assertTrue(response.body().contains(OTHER_HTML_CONTENT));
                assertTrue(
                        response.headers()
                                .firstValue("Content-Type")
                                .orElse("")
                                .startsWith("text/html")
                );
            }
        }
    }

    /**
     * SPA fallback is enabled at the default ({@code "/"}) URL prefix, alongside a real
     * JAX-RS resource ({@code GET /ping}) that is itself extensionless. Before this
     * resource was implemented as a low-priority JAX-RS resource (registered by
     * {@code DefaultServer.buildResourceConfig()}) rather than served unconditionally by
     * {@link StaticHandler} ahead of Jersey, enabling {@code spaFallback()} at the root
     * prefix would have silently shadowed every such endpoint with a {@code 200 text/html}
     * {@code index.html} body instead of the real response — see {@code StaticHandler}'s
     * Javadoc and {@code DefaultServer.buildResourceConfig()}'s SPA fallback comment for
     * the full root-cause writeup. This regression-tests the fix: a real, literal-path
     * JAX-RS resource must always win over the {@code {var:.*}} fallback template.
     */
    @Nested
    class SpaFallbackDoesNotShadowJaxRsEndpoint {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                    .spaFallback()
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void extensionlessJaxRsEndpoint_returnsRealResponse_notIndexHtml() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
            assertTrue(response.body().contains("pong"));
            assertFalse(response.body().contains(INDEX_HTML_CONTENT));
        }

        @Test
        void extensionlessJaxRsEndpointWithPathParam_returnsRealResponse_notIndexHtml() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping/some-id"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("some-id"));
            assertFalse(response.body().contains(INDEX_HTML_CONTENT));
        }

        @Test
        void unmatchedExtensionlessPath_stillServesIndexHtml() throws Exception {
            // Confirms the fallback itself is still intact for paths no JAX-RS resource claims.
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }
    }

    // -------------------------------------------------------------------------
    // Error pages
    // -------------------------------------------------------------------------

    @Nested
    class ErrorPage {
        @Nested
        class Configured {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .errorPage(404, "404.html")
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void missingPath_returns404WithCustomPageBody() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/missing"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
                assertTrue(response.body().contains(CUSTOM_404_CONTENT));
            }
        }

        @Nested
        class NotConfigured {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void missingPath_returns404WithoutCustomPageBody() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/missing"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
                assertFalse(response.body().contains(CUSTOM_404_CONTENT));
            }
        }

        /**
         * The configured error-page path has an unrecognized file extension, so
         * {@code MimeTypes.DEFAULTS.getMimeByExtension()} returns {@code null} and the
         * handler must fall back to {@code "text/html; charset=utf-8"}.  This exercises
         * the {@code null != contentType} false branch in {@code serveErrorPage()}.
         */
        @Nested
        class UnknownExtension {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .errorPage(404, "custom-404.404")
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void missingPath_unknownExtension_usesHtmlFallbackContentType() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/missing"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
                assertTrue(response.body().contains(CUSTOM_404_CONTENT));
                assertTrue(
                        response.headers()
                                .firstValue("Content-Type")
                                .orElse("")
                                .startsWith("text/html")
                );
            }
        }

        /**
         * The configured error-page file exists at startup (so validation passes) but is
         * deleted before the first request arrives.  The handler must fall back to a plain
         * error response rather than throwing, covering the
         * {@code !Resources.isReadableFile(errorPageResource)} guard in
         * {@code serveErrorPage()}.
         */
        @Nested
        class DeletedAfterStartup {
            @TempDir
            Path tmpDir;

            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                Files.writeString(tmpDir.resolve("index.html"), INDEX_HTML_CONTENT);
                Files.writeString(tmpDir.resolve("404.html"), CUSTOM_404_CONTENT);

                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.filesystem(tmpDir)
                                        .errorPage(404, "404.html")
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();

                Files.delete(tmpDir.resolve("404.html"));
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void missingPath_errorPageGone_returnsPlain404() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/missing"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
                assertFalse(response.body().contains(CUSTOM_404_CONTENT));
            }
        }

        /**
         * Multiple error page codes configured: 404 and 500.  Verifies that both
         * are served correctly for their respective scenarios.
         */
        @Nested
        class MultipleStatusCodes {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .errorPage(404, "404.html")
                                        .errorPage(500, "500.html")
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void missingPath_returns404WithCustomPage() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/missing"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(404, response.statusCode());
                assertTrue(response.body().contains(CUSTOM_404_CONTENT));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dotfile protection
    // -------------------------------------------------------------------------

    @Nested
    class DotfileProtection {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void dotfilePath_returns404() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/.env"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(404, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Security (response) headers
    // -------------------------------------------------------------------------

    @Nested
    class SecurityHeaders {
        private static final String HEADER_NAME = "X-Frame-Options";
        private static final String HEADER_VALUE = "DENY";

        @Nested
        class Configured {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .responseHeaders(Map.of(HEADER_NAME, HEADER_VALUE))
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void assetResponse_includesConfiguredHeader() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
                assertEquals(HEADER_VALUE, response.headers().firstValue(HEADER_NAME).orElse(null));
            }

            @Test
            void jaxRsResponse_doesNotIncludeAssetHeader() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/ping"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
                assertFalse(response.headers().firstValue(HEADER_NAME).isPresent());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cache-Control
    // -------------------------------------------------------------------------

    @Nested
    class CacheControl {
        @Nested
        class PositiveDuration {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .cacheMaxAge(Duration.ofDays(7))
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void assetResponse_includesCacheControlPublic() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
                assertEquals(
                        "max-age=604800, public",
                        response.headers().firstValue("Cache-Control").orElse(null)
                );
            }
        }

        @Nested
        class ZeroDuration {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .cacheMaxAge(Duration.ZERO)
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void assetResponse_includesNoCacheDirective() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
                assertEquals(
                        "max-age=0, no-cache",
                        response.headers().firstValue("Cache-Control").orElse(null)
                );
            }
        }

        @Nested
        class NotConfigured {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void assetResponse_hasNoCacheControlHeader() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
                assertFalse(response.headers().firstValue("Cache-Control").isPresent());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Auth filter
    // -------------------------------------------------------------------------

    @Nested
    class AuthFilter {
        @Nested
        class Allows {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .authFilter((req, res) -> true)
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void filterReturnsTrue_fileIsServed() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(200, response.statusCode());
            }
        }

        @Nested
        class Rejects {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .authFilter((req, res) -> {
                                            Response.writeError(req, res, Callback.NOOP, HttpStatus.UNAUTHORIZED_401);
                                            return false;
                                        })
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void filterReturnsFalse_returns401() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(401, response.statusCode());
            }
        }

        /**
         * Filter sets status 429 and returns false without writing a body.
         * A custom 429 error page is configured — the handler must serve it.
         */
        @Nested
        class RejectsWithErrorPage {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .errorPage(429, "429.html")
                                        .authFilter((req, res) -> {
                                            res.setStatus(429);
                                            return false;
                                        })
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void filterSets429WithoutBody_servesCustom429Page() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(429, response.statusCode());
                assertTrue(response.body().contains(CUSTOM_429_CONTENT));
            }
        }

        /**
         * Filter throws a RuntimeException.  A custom 500 error page is configured —
         * the handler must catch the exception and serve it.
         */
        @Nested
        class ThrowsWithErrorPage {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .errorPage(500, "500.html")
                                        .authFilter((req, res) -> {
                                            throw new RuntimeException("simulated auth backend failure");
                                        })
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void filterThrows_servesCustom500Page() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(500, response.statusCode());
                assertTrue(response.body().contains(CUSTOM_500_CONTENT));
            }
        }

        /**
         * Filter throws a RuntimeException but no 500 error page is configured.
         * The handler must still return a 500 response via the plain error mechanism.
         */
        @Nested
        class ThrowsWithoutErrorPage {
            private Server server;
            private URI baseUri;

            @BeforeEach
            void setUp() throws Exception {
                server = Server.builder()
                        .configuration(
                                ServerConfiguration.builder()
                                        .port(0)
                                        .serializer(new TestJsonSerializer())
                                        .build()
                        )
                        .resources(new PingResource())
                        .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                        .staticAssets(
                                StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                        .authFilter((req, res) -> {
                                            throw new RuntimeException("simulated auth backend failure");
                                        })
                                        .build()
                        )
                        .build();
                server.start();
                baseUri = server.uri();
            }

            @AfterEach
            void tearDown() {
                server.stop();
            }

            @Test
            void filterThrows_returnsPlain500() throws Exception {
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                assertEquals(500, response.statusCode());
                assertFalse(response.body().contains(CUSTOM_500_CONTENT));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Multiple asset roots
    // -------------------------------------------------------------------------

    @Nested
    class MultipleAssetRoots {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                    .urlPrefix("/ui")
                                    .build(),
                            StaticAssetsConfiguration.classpath(CLASSPATH_ALT_ASSET_ROOT)
                                    .urlPrefix("/docs")
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void uiRoot_servesUiAssets() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ui/index.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }

        @Test
        void docsRoot_servesDocsAssets() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/docs/other.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(OTHER_HTML_CONTENT));
        }

        @Test
        void uiRoot_doesNotServeDocsAssets() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ui/other.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(404, response.statusCode());
        }

        @Test
        void docsRoot_doesNotServeUiAssets() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/docs/index.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(404, response.statusCode());
        }

        @Test
        void exactPrefixPath_servesIndexHtml() throws Exception {
            // GET /ui — path exactly equals the URL prefix (no trailing slash, no sub-path).
            // This exercises the path.equals(urlPrefix) branch of matchesPrefix().
            // The prefix is stripped to "/" and the welcome file index.html is served.
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ui"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }
    }

    /**
     * SPA fallback combined with a non-root {@code urlPrefix} — exercises the
     * {@code urlPrefix + "/{path:.*}"} path-building branch in
     * {@code DefaultServer.buildResourceConfig()} (the root-prefix case is already covered
     * by {@link SpaFallback}).
     */
    @Nested
    class SpaFallbackWithUrlPrefix {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                    .urlPrefix("/ui")
                                    .spaFallback()
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void deepLinkUnderPrefix_servesIndexHtml() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ui/some/deep/route"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }

        @Test
        void jaxRsEndpointOutsidePrefix_stillRespondsNormally() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("pong"));
        }

        @Test
        void deepLinkOutsidePrefix_returns404() throws Exception {
            // Not under /ui, and no JAX-RS resource matches — the fallback resource
            // registered for the "/ui" prefix must not claim this path.
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(404, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Conflict detection
    // -------------------------------------------------------------------------

    @Nested
    class ConflictDetection {
        @Test
        void duplicateUrlPrefix_throwsIllegalStateException() {
            assertThrows(
                    IllegalStateException.class,
                    () -> Server.builder()
                            .configuration(
                                    ServerConfiguration.builder()
                                            .port(0)
                                            .serializer(new TestJsonSerializer())
                                            .build()
                            )
                            .staticAssets(
                                    StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                            .urlPrefix("/ui")
                                            .build(),
                                    StaticAssetsConfiguration.classpath(CLASSPATH_ALT_ASSET_ROOT)
                                            .urlPrefix("/ui")
                                            .build()
                            )
                            .build()
            );
        }
    }

    // -------------------------------------------------------------------------
    // Startup validation
    // -------------------------------------------------------------------------

    @Nested
    class StartupValidation {
        private static final String CLASSPATH_FILE_SOURCE_MESSAGE =
                "The 'resourcePath' value of 'classpath:/static-test-assets/index.html' is invalid.  "
                        + "The resource does not exist or is not a directory.";
        private static final String CLASSPATH_MISSING_SOURCE_MESSAGE =
                "The 'resourcePath' value of 'classpath:/does-not-exist' is invalid.  "
                        + "The resource does not exist or is not a directory.";
        private static final String NOT_FOUND_PAGE_MISSING_MESSAGE =
                "The 'errorPage[404]' value of 'missing-404.html' is invalid.  "
                        + "The file does not exist in the configured asset root.";
        private static final String SPA_FALLBACK_MISSING_INDEX_MESSAGE =
                "The 'spaFallback' configuration for 'classpath:" + CLASSPATH_ALT_ASSET_ROOT + "' is invalid.  "
                        + "The asset root does not contain a readable 'index.html' file.";
        private static final String SPA_FALLBACK_CLASSPATH_MISSING_SOURCE_MESSAGE =
                "The 'resourcePath' value of 'classpath:/does-not-exist' is invalid.  "
                        + "The resource does not exist or is not a directory.";
        private static final String SPA_FALLBACK_CLASSPATH_FILE_SOURCE_MESSAGE =
                "The 'resourcePath' value of 'classpath:/static-test-assets/index.html' is invalid.  "
                        + "The resource does not exist or is not a directory.";
        @TempDir
        Path tempDir;

        private static void assertStartupIllegalStateException(Server server, String expectedMessage) {
            // Jetty may wrap the IllegalStateException in its lifecycle machinery.
            Throwable cause = assertThrows(Throwable.class, server::start);
            while (null != cause && !(cause instanceof IllegalStateException)) {
                cause = cause.getCause();
            }

            assertInstanceOf(IllegalStateException.class, cause);
            assertEquals(expectedMessage, cause.getMessage());
        }

        @Test
        void classpathSourcePointingToFile_throwsOnStart() {
            // /static-test-assets/index.html is a file, not a directory —
            // the builder accepts it (classpath existence is not validated at
            // build time) but the server must reject it on startup.
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath("/static-test-assets/index.html")
                                    .build()
                    )
                    .build();

            assertStartupIllegalStateException(server, CLASSPATH_FILE_SOURCE_MESSAGE);
        }

        @Test
        void classpathSourceNotFound_throwsOnStart() {
            // /does-not-exist is absent from the classpath entirely —
            // newClassLoaderResource() returns null, exercising the null == baseResource branch.
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath("/does-not-exist")
                                    .build()
                    )
                    .build();

            assertStartupIllegalStateException(server, CLASSPATH_MISSING_SOURCE_MESSAGE);
        }

        @Test
        void filesystemSourceDeletedBeforeStart_throwsOnStart() throws Exception {
            // Create a subdirectory so the builder accepts it, then delete it before
            // the server starts — exercises the filesystem branch of sourceArgumentName().
            Path subDir = Files.createDirectory(tempDir.resolve("assets"));

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(subDir)
                                    .build()
                    )
                    .build();

            Files.delete(subDir);

            String expectedMessage =
                    "The 'directory' value of '" + subDir + "' is invalid.  "
                            + "The resource does not exist or is not a directory.";

            assertStartupIllegalStateException(server, expectedMessage);
        }

        @Test
        void errorPageNotInAssetRoot_throwsOnStart() {
            // missing-404.html does not exist in /static-test-assets —
            // the server must reject it during startup validation.
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                    .errorPage(404, "missing-404.html")
                                    .build()
                    )
                    .build();

            assertStartupIllegalStateException(server, NOT_FOUND_PAGE_MISSING_MESSAGE);
        }

        @Test
        void spaFallbackEnabledWithoutIndexHtml_throwsOnStart() {
            // CLASSPATH_ALT_ASSET_ROOT has no index.html — the server must reject this
            // configuration during startup validation rather than only discovering it lazily
            // on the first deep-link request.
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ALT_ASSET_ROOT)
                                    .spaFallback()
                                    .build()
                    )
                    .build();

            assertStartupIllegalStateException(server, SPA_FALLBACK_MISSING_INDEX_MESSAGE);
        }

        @Test
        void spaFallbackEnabledWithClasspathSourceNotFound_throwsOnStart() {
            // The spaFallback loop in buildResourceConfig() resolves and validates the base
            // resource itself, before StaticHandler.doStart() ever runs — exercising the
            // null == baseResource branch of that independent validation, not the one already
            // covered by classpathSourceNotFound_throwsOnStart (which has spaFallback() disabled).
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath("/does-not-exist")
                                    .spaFallback()
                                    .build()
                    )
                    .build();

            assertStartupIllegalStateException(server, SPA_FALLBACK_CLASSPATH_MISSING_SOURCE_MESSAGE);
        }

        @Test
        void spaFallbackEnabledWithClasspathSourcePointingToFile_throwsOnStart() {
            // Same as above, exercising the other operand of the spaFallback loop's
            // "null == baseResource || !baseResource.isDirectory()" check: the resource
            // resolves but is a file, not a directory.
            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath("/static-test-assets/index.html")
                                    .spaFallback()
                                    .build()
                    )
                    .build();

            assertStartupIllegalStateException(server, SPA_FALLBACK_CLASSPATH_FILE_SOURCE_MESSAGE);
        }

        @Test
        void spaFallbackEnabledWithFilesystemSourceDeleted_throwsOnStart() throws Exception {
            // Exercises the other branch of the spaFallback loop's ternary —
            // classpathResourcePath().isPresent() == false, so sourceArgumentName is
            // "directory" rather than "resourcePath" — which neither of the two classpath-based
            // tests above can reach.
            Path subDir = Files.createDirectory(tempDir.resolve("assets"));

            Server server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(subDir)
                                    .spaFallback()
                                    .build()
                    )
                    .build();

            Files.delete(subDir);

            String expectedMessage =
                    "The 'directory' value of '" + subDir + "' is invalid.  "
                            + "The resource does not exist or is not a directory.";

            assertStartupIllegalStateException(server, expectedMessage);
        }
    }

    // -------------------------------------------------------------------------
    // SPA fallback response parity — Cache-Control, conditional requests, preCompressed
    // -------------------------------------------------------------------------

    /**
     * {@code SpaFallbackInflector}'s response must behave the same, with respect to
     * {@code cacheMaxAge()}, {@code ETag}/{@code Last-Modified} conditional-GET support, and
     * {@code preCompressed()}, as a direct request for a real static file (already exercised
     * for real files by {@link CacheControl}, {@link ConditionalRequests}, and
     * {@link PreCompressed}).  Uses a filesystem source so compressed siblings of
     * {@code index.html} can be generated programmatically.
     */
    @Nested
    class SpaFallbackResponseParity {
        private static final String HEADER_NAME = "X-Frame-Options";
        private static final String HEADER_VALUE = "DENY";

        @TempDir
        Path tempDir;

        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            Files.writeString(
                    tempDir.resolve("index.html"),
                    "<!DOCTYPE html><html><body>" + INDEX_HTML_CONTENT + "</body></html>"
            );

            try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(
                    Files.newOutputStream(tempDir.resolve("index.html.gz")))) {
                gzip.write(INDEX_HTML_CONTENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            // Placeholder brotli sibling — Java's HttpClient does not decode brotli, so
            // brotli-related tests verify only the Content-Encoding header, not the body
            // (same approach as PreCompressed.setUp's app.js.br).
            Files.write(tempDir.resolve("index.html.br"), new byte[]{0x01, 0x00});

            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(tempDir)
                                    .spaFallback()
                                    .cacheMaxAge(Duration.ofDays(7))
                                    .preCompressed()
                                    .responseHeaders(Map.of(HEADER_NAME, HEADER_VALUE))
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void fallbackResponse_includesCacheControlHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(
                    "max-age=604800, public",
                    response.headers().firstValue("Cache-Control").orElse(null)
            );
        }

        @Test
        void fallbackResponse_includesETagAndLastModified() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("ETag").isPresent());
            assertTrue(response.headers().firstValue("Last-Modified").isPresent());
        }

        @Test
        void fallbackResponse_matchingETag_returns304NotModified() throws Exception {
            HttpResponse<String> firstResponse = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            String etag = firstResponse.headers().firstValue("ETag").orElse(null);
            assertNotNull(etag);

            HttpResponse<String> conditionalResponse = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/other/deep/route"))
                            .header("If-None-Match", etag)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(304, conditionalResponse.statusCode());
        }

        @Test
        void fallbackResponse_gzipAccepted_servesCompressedIndexHtml() throws Exception {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .header("Accept-Encoding", "gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElse(""));
            assertEquals("Accept-Encoding", response.headers().firstValue("Vary").orElse(""));

            try (java.util.zip.GZIPInputStream gzipIn = new java.util.zip.GZIPInputStream(
                    new java.io.ByteArrayInputStream(response.body()))) {
                assertEquals(
                        INDEX_HTML_CONTENT,
                        new String(gzipIn.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                );
            }
        }

        @Test
        void fallbackResponse_brotliAccepted_servesBrotliSibling() throws Exception {
            // index.html.br contains placeholder bytes — Java's HttpClient does not decode
            // brotli, so only the Content-Encoding header is verified (same approach as
            // PreCompressed.brotliAccepted tests for real static files).
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .header("Accept-Encoding", "br")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertEquals("br", response.headers().firstValue("Content-Encoding").orElse(""));
            assertEquals("Accept-Encoding", response.headers().firstValue("Vary").orElse(""));
        }

        @Test
        void fallbackResponse_bothEncodingsAccepted_prefersBrotli() throws Exception {
            // selectCompressedVariant() checks "br" before "gzip" — when the client
            // advertises both, Brotli must win, matching CompressedContentFormat.BR,
            // CompressedContentFormat.GZIP precedence on the real ResourceHandler.
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .header("Accept-Encoding", "gzip, br")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertEquals("br", response.headers().firstValue("Content-Encoding").orElse(""));
        }

        @Test
        void fallbackResponse_includesConfiguredResponseHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(HEADER_VALUE, response.headers().firstValue(HEADER_NAME).orElse(null));
        }

        @Test
        void fallbackResponse_unsupportedEncodingAccepted_servesPlainIndexHtml() throws Exception {
            // Neither "br" nor "gzip" appears in Accept-Encoding — selectCompressedVariant()
            // must fall through both checks and return null without ever consulting the real
            // gzResource, even though index.html.gz exists in this asset root.
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .header("Accept-Encoding", "deflate")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Encoding").isEmpty());
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }
    }

    /**
     * Covers the {@code !Resources.isReadableFile(brResource)} branch of
     * {@code SpaFallbackInflector.selectCompressedVariant()}: the client accepts Brotli but
     * {@code index.html.br} does not exist, so selection must fall through to the gzip check
     * below it rather than serving the uncompressed file.  {@link SpaFallbackResponseParity}'s
     * asset root always has both siblings, so it cannot exercise this branch.
     */
    @Nested
    class SpaFallbackBrotliSiblingMissing {
        @TempDir
        Path tempDir;

        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            Files.writeString(
                    tempDir.resolve("index.html"),
                    "<!DOCTYPE html><html><body>" + INDEX_HTML_CONTENT + "</body></html>"
            );

            // Only a gzip sibling exists — no index.html.br.
            try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(
                    Files.newOutputStream(tempDir.resolve("index.html.gz")))) {
                gzip.write(INDEX_HTML_CONTENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(tempDir)
                                    .spaFallback()
                                    .preCompressed()
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void brotliAcceptedButNoSibling_fallsBackToGzip() throws Exception {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .header("Accept-Encoding", "br, gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElse(""));

            try (java.util.zip.GZIPInputStream gzipIn = new java.util.zip.GZIPInputStream(
                    new java.io.ByteArrayInputStream(response.body()))) {
                assertEquals(
                        INDEX_HTML_CONTENT,
                        new String(gzipIn.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                );
            }
        }
    }

    /**
     * Covers the {@code !Resources.isReadableFile(gzResource)} branch of
     * {@code SpaFallbackInflector.selectCompressedVariant()}: the client accepts gzip but
     * neither {@code index.html.br} nor {@code index.html.gz} exist, so selection must fall
     * through both checks and return {@code null} — serving the plain, uncompressed
     * {@code index.html} with no {@code Content-Encoding} header.  Both
     * {@link SpaFallbackResponseParity} and {@link SpaFallbackBrotliSiblingMissing} always
     * have a gzip sibling present, so neither can exercise this branch.
     */
    @Nested
    class SpaFallbackNoCompressedSiblings {
        @TempDir
        Path tempDir;

        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            // Only the plain file exists — no index.html.br, no index.html.gz.
            Files.writeString(
                    tempDir.resolve("index.html"),
                    "<!DOCTYPE html><html><body>" + INDEX_HTML_CONTENT + "</body></html>"
            );

            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(tempDir)
                                    .spaFallback()
                                    .preCompressed()
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void gzipAcceptedButNoSiblingAtAll_servesPlainIndexHtml() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/some/deep/route"))
                            .header("Accept-Encoding", "gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Encoding").isEmpty());
            assertTrue(response.body().contains(INDEX_HTML_CONTENT));
        }
    }

    // -------------------------------------------------------------------------
    // JAX-RS priority
    // -------------------------------------------------------------------------

    @Nested
    class JaxRsPriority {
        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.classpath(CLASSPATH_ASSET_ROOT)
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void jaxRsEndpoint_respondsNormallyWhenNoMatchingStaticFile() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/ping"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Conditional requests (ETag / Last-Modified)
    // -------------------------------------------------------------------------

    @Nested
    class ConditionalRequests {
        @TempDir
        Path tempDir;

        private Server server;
        private URI baseUri;

        @BeforeEach
        void setUp() throws Exception {
            Files.writeString(
                    tempDir.resolve("index.html"),
                    "<!DOCTYPE html><html><body>" + INDEX_HTML_CONTENT + "</body></html>"
            );

            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(tempDir)
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void assetResponse_includesETagHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("ETag").isPresent());
        }

        @Test
        void assetResponse_includesLastModifiedHeader() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Last-Modified").isPresent());
        }

        @Test
        void matchingETag_returns304NotModified() throws Exception {
            HttpResponse<String> firstResponse = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, firstResponse.statusCode());

            String etag = firstResponse.headers().firstValue("ETag").orElse(null);
            assertNotNull(etag);

            HttpResponse<String> conditionalResponse = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/index.html"))
                            .header("If-None-Match", etag)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(304, conditionalResponse.statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Pre-compressed serving
    // -------------------------------------------------------------------------

    /**
     * Tests pre-compressed serving using a filesystem source so that compressed
     * sibling files can be generated programmatically in {@code @BeforeEach}.
     * <p>
     * {@code app.js.gz} is a real gzip stream produced by {@link java.util.zip.GZIPOutputStream}.
     * {@code app.js.br} contains placeholder bytes — Java's {@link HttpClient} does not
     * automatically decompress brotli, so the test verifies only the
     * {@code Content-Encoding: br} header rather than the decompressed body.
     */
    @Nested
    class PreCompressed {
        private static final String APP_JS_CONTENT = "console.log('precompressed-test');";
        private static final String NO_SIBLING_CONTENT = "console.log('no-sibling');";

        @TempDir
        Path tempDir;

        private Server server;
        private Server serverDisabled;
        private URI baseUri;
        private URI baseUriDisabled;

        @BeforeEach
        void setUp() throws Exception {
            // Write plain source files.
            Files.writeString(tempDir.resolve("app.js"), APP_JS_CONTENT);
            Files.writeString(tempDir.resolve("no-sibling.js"), NO_SIBLING_CONTENT);

            // Write a real gzip-compressed sibling.
            try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(
                    java.nio.file.Files.newOutputStream(tempDir.resolve("app.js.gz")))) {
                gzip.write(APP_JS_CONTENT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            // Write placeholder brotli sibling — Jetty serves it as-is with Content-Encoding: br.
            Files.write(tempDir.resolve("app.js.br"), new byte[]{0x01, 0x00});

            server = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(tempDir)
                                    .preCompressed()
                                    .build()
                    )
                    .build();
            server.start();
            baseUri = server.uri();

            serverDisabled = Server.builder()
                    .configuration(
                            ServerConfiguration.builder()
                                    .port(0)
                                    .serializer(new TestJsonSerializer())
                                    .build()
                    )
                    .resources(new PingResource())
                    .components(TestLogging.forClass(ServerStaticAssetsTest.class))
                    .staticAssets(
                            StaticAssetsConfiguration.filesystem(tempDir)
                                    .build()
                    )
                    .build();
            serverDisabled.start();
            baseUriDisabled = serverDisabled.uri();
        }

        @AfterEach
        void tearDown() {
            server.stop();
            serverDisabled.stop();
        }

        @Test
        void gzipAccepted_servesGzipSibling() throws Exception {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/app.js"))
                            .header("Accept-Encoding", "gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElse(""));

            try (java.util.zip.GZIPInputStream gzipIn = new java.util.zip.GZIPInputStream(
                    new java.io.ByteArrayInputStream(response.body()))) {
                String decompressed = new String(
                        gzipIn.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                );
                assertEquals(APP_JS_CONTENT, decompressed);
            }
        }

        @Test
        void brotliAccepted_servesBrotliSibling() throws Exception {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/app.js"))
                            .header("Accept-Encoding", "br")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertEquals("br", response.headers().firstValue("Content-Encoding").orElse(""));
        }

        @Test
        void bothAccepted_prefersBrotli() throws Exception {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/app.js"))
                            .header("Accept-Encoding", "br, gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            assertEquals(200, response.statusCode());
            assertEquals("br", response.headers().firstValue("Content-Encoding").orElse(""));
        }

        @Test
        void noSibling_servesUncompressed() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUri.resolve("/no-sibling.js"))
                            .header("Accept-Encoding", "gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Encoding").isEmpty());
            assertEquals(NO_SIBLING_CONTENT, response.body());
        }

        @Test
        void notEnabled_servesUncompressedEvenWithSibling() throws Exception {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(baseUriDisabled.resolve("/app.js"))
                            .header("Accept-Encoding", "gzip")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Encoding").isEmpty());
            assertEquals(APP_JS_CONTENT, response.body());
        }
    }
}
