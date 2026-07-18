package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.HttpResponseException;
import software.frisby.web.client.exception.ServerException;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExceptionFactory}.
 */
class ExceptionFactoryTest {

    @Nested
    class IsError {
        @Test
        void successStatusCode_returnsFalse() {
            assertFalse(ExceptionFactory.isError(200));
        }

        @Test
        void clientErrorStatusCode_returnsTrue() {
            assertTrue(ExceptionFactory.isError(400));
        }

        @Test
        void serverErrorStatusCode_returnsTrue() {
            assertTrue(ExceptionFactory.isError(500));
        }
    }

    @Nested
    class Create {
        /**
         * Calling {@code create()} directly with a redirect status code exercises the
         * {@code default} branch where {@code statusCode >= 400} is {@code false},
         * which is unreachable from production code (guarded by {@code isError()}).
         */
        @Test
        void redirectStatusCode_yieldsServerException() {
            HttpHeaders headers = HttpHeaders.of(Map.of(), (k, v) -> true);
            URI uri = URI.create("https://api.example.com/orders");

            HttpResponseException ex = ExceptionFactory.create(null, "GET", uri, 302, headers);

            assertInstanceOf(ServerException.class, ex);
        }
    }
}

