package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.ServerException;
import software.frisby.web.client.exception.TransportException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HttpEngineTest {
    @Nested
    class UnwrapThrowable {
        @Test
        void unwrapCompletionException_nonCompletionException_returnedUnchanged() {
            Throwable cause = new UnsupportedOperationException();
            Throwable actual = HttpEngine.unwrapCompletionException(cause);

            assertEquals(cause, actual);
        }

        @Test
        void unwrapCompletionException_completionException_causeUnwrapped() {
            RuntimeException inner = new RuntimeException("inner");
            Throwable actual = HttpEngine.unwrapCompletionException(new CompletionException(inner));

            assertEquals(inner, actual);
        }

        @Test
        void unwrapHttpResponseException_nonIOException_returnedUnchanged() {
            Throwable cause = new UnsupportedOperationException();
            Throwable actual = HttpEngine.unwrapHttpResponseException(cause);

            assertEquals(cause, actual);
        }

        @Test
        void unwrapHttpResponseException_ioExceptionWithoutHttpResponseException_returnedUnchanged() {
            Throwable cause = new IOException();
            Throwable actual = HttpEngine.unwrapHttpResponseException(cause);

            assertEquals(cause, actual);
        }

        @Test
        void unwrapHttpResponseException_ioExceptionWrappingHttpResponseException_innerExceptionUnwrapped() {
            Throwable cause = new ServerException(500);
            Throwable actual = HttpEngine.unwrapHttpResponseException(new IOException(cause));

            assertEquals(cause, actual);
        }

        @Test
        void wrapIfIOException_nonIOException_returnedUnchanged() {
            Throwable cause = new RuntimeException();
            Throwable actual = HttpEngine.wrapIfIOException(
                    cause,
                    "GET",
                    URI.create("https://example.com")
            );

            assertEquals(cause, actual);
        }

        @Test
        void wrapIfIOException_ioException_wrappedInTransportException() {
            String method = "GET";
            URI uri = URI.create("https://example.com");

            Throwable cause = new IOException();
            Throwable actual = HttpEngine.wrapIfIOException(cause, method, uri);

            assertInstanceOf(TransportException.class, actual);

            TransportException transportException = (TransportException) actual;
            assertEquals(method, transportException.method().orElseThrow());
            assertEquals(uri, transportException.uri().orElseThrow());
        }
    }
}
