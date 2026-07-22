package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.web.client.exception.*;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExceptionFactory}.
 */
class ExceptionFactoryTest {
    private static final URI URI = java.net.URI.create("https://api.example.com/orders");
    private static final HttpHeaders EMPTY = HttpHeaders.of(Map.of(), (k, v) -> true);

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
            assertInstanceOf(ServerException.class,
                    ExceptionFactory.create(null, "GET", URI, 302, EMPTY));
        }

        // 4xx — specific mappings

        @Test
        void status400_yieldsBadRequestException() {
            assertInstanceOf(BadRequestException.class,
                    ExceptionFactory.create(null, "GET", URI, 400, EMPTY));
        }

        @Test
        void status401_yieldsUnauthorizedException() {
            assertInstanceOf(UnauthorizedException.class,
                    ExceptionFactory.create(null, "GET", URI, 401, EMPTY));
        }

        @Test
        void status403_yieldsForbiddenException() {
            assertInstanceOf(ForbiddenException.class,
                    ExceptionFactory.create(null, "GET", URI, 403, EMPTY));
        }

        @Test
        void status404_yieldsNotFoundException() {
            assertInstanceOf(NotFoundException.class,
                    ExceptionFactory.create(null, "GET", URI, 404, EMPTY));
        }

        @Test
        void status405_yieldsMethodNotAllowedException() {
            assertInstanceOf(MethodNotAllowedException.class,
                    ExceptionFactory.create(null, "GET", URI, 405, EMPTY));
        }

        @Test
        void status406_yieldsNotAcceptableException() {
            assertInstanceOf(NotAcceptableException.class,
                    ExceptionFactory.create(null, "GET", URI, 406, EMPTY));
        }

        @Test
        void status408_yieldsRequestTimeoutException() {
            assertInstanceOf(RequestTimeoutException.class,
                    ExceptionFactory.create(null, "GET", URI, 408, EMPTY));
        }

        @Test
        void status409_yieldsConflictException() {
            assertInstanceOf(ConflictException.class,
                    ExceptionFactory.create(null, "POST", URI, 409, EMPTY));
        }

        @Test
        void status410_yieldsGoneException() {
            assertInstanceOf(GoneException.class,
                    ExceptionFactory.create(null, "GET", URI, 410, EMPTY));
        }

        @Test
        void status413_yieldsPayloadTooLargeException() {
            assertInstanceOf(PayloadTooLargeException.class,
                    ExceptionFactory.create(null, "POST", URI, 413, EMPTY));
        }

        @Test
        void status415_yieldsUnsupportedMediaTypeException() {
            assertInstanceOf(UnsupportedMediaTypeException.class,
                    ExceptionFactory.create(null, "POST", URI, 415, EMPTY));
        }

        @Test
        void status422_yieldsUnprocessableEntityException() {
            assertInstanceOf(UnprocessableEntityException.class,
                    ExceptionFactory.create(null, "POST", URI, 422, EMPTY));
        }

        @Test
        void status429_yieldsTooManyRequestsException() {
            assertInstanceOf(TooManyRequestsException.class,
                    ExceptionFactory.create(null, "GET", URI, 429, EMPTY));
        }

        @Test
        void unmapped4xx_yieldsClientException() {
            HttpResponseException ex = ExceptionFactory.create(null, "GET", URI, 418, EMPTY);

            assertInstanceOf(ClientException.class, ex);
            assertEquals(418, ex.statusCode());
        }

        // 5xx — specific mappings

        @Test
        void status500_yieldsInternalServerErrorException() {
            assertInstanceOf(InternalServerErrorException.class,
                    ExceptionFactory.create(null, "GET", URI, 500, EMPTY));
        }

        @Test
        void status501_yieldsNotImplementedException() {
            assertInstanceOf(NotImplementedException.class,
                    ExceptionFactory.create(null, "GET", URI, 501, EMPTY));
        }

        @Test
        void status502_yieldsBadGatewayException() {
            assertInstanceOf(BadGatewayException.class,
                    ExceptionFactory.create(null, "GET", URI, 502, EMPTY));
        }

        @Test
        void status503_yieldsServiceUnavailableException() {
            assertInstanceOf(ServiceUnavailableException.class,
                    ExceptionFactory.create(null, "GET", URI, 503, EMPTY));
        }

        @Test
        void status504_yieldsGatewayTimeoutException() {
            assertInstanceOf(GatewayTimeoutException.class,
                    ExceptionFactory.create(null, "GET", URI, 504, EMPTY));
        }

        @Test
        void unmapped5xx_yieldsServerException() {
            HttpResponseException ex = ExceptionFactory.create(null, "GET", URI, 507, EMPTY);

            assertInstanceOf(ServerException.class, ex);
            assertEquals(507, ex.statusCode());
        }
    }
}

