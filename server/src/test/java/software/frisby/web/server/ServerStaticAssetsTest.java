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
                                        .spaFallback(true)
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
         * SPA fallback is enabled but the asset root contains no {@code index.html}.
         * When an extensionless path misses and the fallback attempt for {@code index.html}
         * also misses, the server must return {@code 404} rather than silently doing nothing.
         * This exercises the {@code served = false, notFoundPage empty} branch in
         * {@link StaticHandler}'s SPA-fallback path.
         */
        @Nested
        class EnabledNoIndexHtml {
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
                                StaticAssetsConfiguration.classpath(CLASSPATH_ALT_ASSET_ROOT)
                                        .spaFallback(true)
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
            void extensionlessPath_indexHtmlMissing_returns404() throws Exception {
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
         * SPA fallback is enabled, the asset root contains no {@code index.html}, and a
         * custom 404 error page is configured.  When an extensionless path misses and the
         * fallback attempt for {@code index.html} also misses, the request falls through to
         * {@code serveErrorPage()} and the custom page body is returned with status
         * {@code 404}.  This exercises the {@code served = false, 404 errorPage present}
         * branch in {@link StaticHandler}'s SPA-fallback path.
         */
        @Nested
        class EnabledNoIndexHtmlWithErrorPage {
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
                                StaticAssetsConfiguration.classpath(CLASSPATH_ALT_ASSET_ROOT)
                                        .spaFallback(true)
                                        .errorPage(404, "other.html")
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
            void extensionlessPath_indexHtmlMissing_servesErrorPage() throws Exception {
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
}




