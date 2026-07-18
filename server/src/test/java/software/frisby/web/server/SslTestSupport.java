package software.frisby.web.server;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * Test helper that loads the pre-generated {@code test.p12} PKCS12 keystore
 * from the test classpath and exposes ready-to-use {@link SSLContext} instances
 * for both sides of an HTTPS connection.
 * <p>
 * The same self-signed certificate is used for both roles:
 * <ul>
 *   <li>{@link #serverSslContext()} — initialised with a {@link KeyManagerFactory}
 *       so the server presents the certificate during TLS negotiation.</li>
 *   <li>{@link #clientSslContext()} — initialised with a {@link TrustManagerFactory}
 *       that trusts the self-signed certificate, so the JDK {@code HttpClient} accepts
 *       the handshake without requiring the cert to be in the system trust store.</li>
 * </ul>
 * <p>
 * Keystore password: {@code changeit}.  To regenerate the certificate (e.g. after
 * expiry in ~10 years) run
 * {@code server/src/test/resources/ssl/generate-keystore.sh}.
 */
final class SslTestSupport {
    private static final String KEYSTORE_RESOURCE = "/ssl/test.p12";
    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

    private SslTestSupport() {
    }

    /**
     * Returns an {@link SSLContext} suitable for the server side of an HTTPS connection.
     * The context is initialised with a {@link KeyManagerFactory} that holds the test
     * certificate's private key, allowing the server to complete TLS negotiation.
     */
    static SSLContext serverSslContext() throws Exception {
        KeyStore keyStore = loadKeyStore();

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);

        return ctx;
    }

    /**
     * Returns an {@link SSLContext} suitable for the client side of an HTTPS connection.
     * The context is initialised with a {@link TrustManagerFactory} that trusts the
     * self-signed test certificate, so connections to {@code https://localhost:…} succeed
     * without the certificate being in the JVM's default trust store.
     */
    static SSLContext clientSslContext() throws Exception {
        KeyStore keyStore = loadKeyStore();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);

        return ctx;
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");

        try (InputStream in = SslTestSupport.class.getResourceAsStream(KEYSTORE_RESOURCE)) {
            ks.load(in, KEYSTORE_PASSWORD);
        }

        return ks;
    }
}

