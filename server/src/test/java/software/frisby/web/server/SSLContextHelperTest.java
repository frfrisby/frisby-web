package software.frisby.web.server;

import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SSLContextHelperTest {
    @Test
    void wrapGetDefault_supplierThrowsNoSuchAlgorithmException_throwsIllegalStateException() {
        SSLContextHelper.SSLContextSupplier supplier = () -> {
            throw new NoSuchAlgorithmException();
        };

        var ex = assertThrows(IllegalStateException.class, () -> SSLContextHelper.wrapGetDefault(supplier));
        assertEquals("The default SSLContext is not available.", ex.getMessage());
    }
}
