package software.frisby.web.server;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;

final class SSLContextHelper {
    private SSLContextHelper() {
    }

    static SSLContext wrapGetDefault(SSLContextSupplier sslContextSupplier) {
        try {
            return sslContextSupplier.get();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("The default SSLContext is not available.", ex);
        }
    }

    @FunctionalInterface
    interface SSLContextSupplier {
        SSLContext get() throws NoSuchAlgorithmException;
    }
}
