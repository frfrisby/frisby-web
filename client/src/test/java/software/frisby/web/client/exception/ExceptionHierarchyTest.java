package software.frisby.web.client.exception;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {
    private static final URI TEST_URI = URI.create("https://api.example.com/orders/42");
    private static final HttpHeaders EMPTY_HEADERS = HttpHeaders.of(Map.of(), (a, b) -> true);

    // -------------------------------------------------------------------------
    // HttpRequestException — edge cases
    // -------------------------------------------------------------------------

    @Nested
    class HttpRequestExceptionEdgeCases {
        /**
         * The 3-arg constructor {@code (Throwable cause, String method, URI uri)} uses
         * {@code null != cause ? cause.toString() : null} as the message.
         * Passing a null cause exercises the {@code null != cause} = false branch.
         */
        @Test
        void nullCause_toStringIncludesContextButNoMessage() {
            var ex = new HttpRequestException(null, "GET", TEST_URI);

            assertEquals("GET", ex.method().orElse(null));
            assertEquals(TEST_URI, ex.uri().orElse(null));
            assertNull(ex.getCause());

            // toString() should include method + URI but have no message section
            String s = ex.toString();

            assertTrue(s.contains("GET"));
            assertTrue(s.contains(TEST_URI.toString()));
        }

        /**
         * When the exception message is blank, {@code toString()} omits the
         * message section — exercises the {@code !message.isBlank()} false branch.
         */
        @Test
        void blankMessage_toStringOmitsMessageSection() {
            var ex = new HttpRequestException("   ", new RuntimeException("cause"), "POST", TEST_URI);

            String s = ex.toString();

            // The blank message itself must not appear in the output — only the class name,
            // method, and URI are present.  Note: the URI contains ':' so we cannot use
            // contains(":") as the sentinel; we verify the trimmed blank is absent instead.
            assertFalse(s.contains(":    "), "blank message section must be omitted");
            assertTrue(s.contains("POST"));
            assertTrue(s.contains(TEST_URI.toString()));
        }
    }

    // -------------------------------------------------------------------------
    // HttpResponseException
    // -------------------------------------------------------------------------

    @Nested
    class HttpResponseExceptionTests {
        @Test
        void knownStatusCodes_resolveToCorrectEnum() {
            var headers = HttpHeaders.of(
                    Map.of("Content-Type", List.of("application/json")),
                    (a, b) -> true
            );

            var ex = new HttpResponseException("GET", TEST_URI, 404, headers, "{\"error\":\"not found\"}");

            assertEquals(404, ex.statusCode());
            assertEquals(ResponseStatus.NOT_FOUND, ex.status());
            assertTrue(ex.method().isPresent());
            assertEquals("GET", ex.method().get());
            assertTrue(ex.uri().isPresent());
            assertEquals(TEST_URI, ex.uri().get());
            assertTrue(ex.body().isPresent());
            assertEquals("{\"error\":\"not found\"}", ex.body().get());
            assertEquals(List.of("application/json"), ex.headers().allValues("Content-Type"));
        }

        @Test
        void statusCodeAndBodyConstructor_methodAndUriAreEmpty() {
            var ex = new HttpResponseException(422, "invalid input");

            assertEquals(422, ex.statusCode());
            assertEquals(ResponseStatus.UNPROCESSABLE_CONTENT, ex.status());
            assertTrue(ex.method().isEmpty());
            assertTrue(ex.uri().isEmpty());
            assertTrue(ex.body().isPresent());
            assertEquals("invalid input", ex.body().get());
        }

        @Test
        void statusCodeOnlyConstructor_bodyIsEmpty() {
            var ex = new HttpResponseException(500);

            assertEquals(500, ex.statusCode());
            assertTrue(ex.body().isEmpty());
        }

        @Test
        void toString_withMethodAndUri_includesBoth() {
            var ex = new HttpResponseException("DELETE", TEST_URI, 404, EMPTY_HEADERS, null);

            String s = ex.toString();

            assertTrue(s.contains("404"));
            assertTrue(s.contains("DELETE"));
            assertTrue(s.contains(TEST_URI.toString()));
        }

        @Test
        void toString_withBody_includesBodyPreview() {
            var ex = new HttpResponseException(400, "bad request body");

            assertTrue(ex.toString().contains("bad request body"));
        }

        @Test
        void toString_withoutMethodAndUri_omitsRequestContext() {
            var ex = new HttpResponseException(503, null);
            String s = ex.toString();

            assertTrue(s.contains("503"));
            assertTrue(s.contains("Service Unavailable"));
        }

        @Test
        void toString_withBlankBody_omitsBodySection() {
            var ex = new HttpResponseException(400, "   ");

            String s = ex.toString();

            assertTrue(s.contains("400"));
            assertFalse(s.contains("   "));
        }

        @Test
        void toString_withLongBody_truncatesAt200Chars() {
            String longBody = "x".repeat(300);
            var ex = new HttpResponseException(400, longBody);

            String s = ex.toString();

            assertTrue(s.contains("…"));
            assertFalse(s.contains(longBody));
        }
    }

    // -------------------------------------------------------------------------
    // HttpResponseException subclass hierarchy
    // -------------------------------------------------------------------------

    @Nested
    class ResponseExceptionSubclasses {
        @Test
        void badRequestException_is400AndClientException() {
            var ex = new BadRequestException("bad");

            assertEquals(400, ex.statusCode());
            assertInstanceOf(ClientException.class, ex);
        }

        @Test
        void unauthorizedException_is401() {
            assertEquals(401, new UnauthorizedException("unauthorized").statusCode());
        }

        @Test
        void forbiddenException_is403() {
            assertEquals(403, new ForbiddenException("forbidden").statusCode());
        }

        @Test
        void notFoundException_is404() {
            assertEquals(404, new NotFoundException("not found").statusCode());
        }

        @Test
        void notFoundException_noArg_is404() {
            assertEquals(404, new NotFoundException().statusCode());
        }

        @Test
        void methodNotAllowedException_is405() {
            assertEquals(405, new MethodNotAllowedException(null).statusCode());
        }

        @Test
        void conflictException_is409() {
            assertEquals(409, new ConflictException(null).statusCode());
        }

        @Test
        void payloadTooLargeException_is413() {
            assertEquals(413, new PayloadTooLargeException(null).statusCode());
        }

        @Test
        void unprocessableEntityException_is422() {
            assertEquals(422, new UnprocessableEntityException(null).statusCode());
        }

        @Test
        void tooManyRequestsException_is429() {
            assertEquals(429, new TooManyRequestsException(null).statusCode());
        }

        @Test
        void internalServerErrorException_is500AndServerException() {
            var ex = new InternalServerErrorException(null);

            assertEquals(500, ex.statusCode());
            assertInstanceOf(ServerException.class, ex);
        }

        @Test
        void notImplementedException_is501() {
            assertEquals(501, new NotImplementedException(null).statusCode());
        }

        @Test
        void serviceUnavailableException_is503() {
            assertEquals(503, new ServiceUnavailableException(null).statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // No-arg constructors (body-less convenience form used by SecurityResponseFilter)
    // -------------------------------------------------------------------------

    @Nested
    class ResponseExceptionNoArgConstructors {
        @Test
        void badRequestException_noArg_is400() {
            assertEquals(400, new BadRequestException().statusCode());
        }

        @Test
        void unauthorizedException_noArg_is401() {
            assertEquals(401, new UnauthorizedException().statusCode());
        }

        @Test
        void forbiddenException_noArg_is403() {
            assertEquals(403, new ForbiddenException().statusCode());
        }

        @Test
        void methodNotAllowedException_noArg_is405() {
            assertEquals(405, new MethodNotAllowedException().statusCode());
        }

        @Test
        void conflictException_noArg_is409() {
            assertEquals(409, new ConflictException().statusCode());
        }

        @Test
        void payloadTooLargeException_noArg_is413() {
            assertEquals(413, new PayloadTooLargeException().statusCode());
        }

        @Test
        void unprocessableEntityException_noArg_is422() {
            assertEquals(422, new UnprocessableEntityException().statusCode());
        }

        @Test
        void tooManyRequestsException_noArg_is429() {
            assertEquals(429, new TooManyRequestsException().statusCode());
        }

        @Test
        void internalServerErrorException_noArg_is500() {
            assertEquals(500, new InternalServerErrorException().statusCode());
        }

        @Test
        void notImplementedException_noArg_is501() {
            assertEquals(501, new NotImplementedException().statusCode());
        }

        @Test
        void serviceUnavailableException_noArg_is503() {
            assertEquals(503, new ServiceUnavailableException().statusCode());
        }
    }

    // -------------------------------------------------------------------------
    // HttpRequestException
    // -------------------------------------------------------------------------

    @Nested
    class HttpRequestExceptionTests {
        @Test
        void fullConstructor_accessorsReturnCorrectValues() {
            var cause = new RuntimeException("connection refused");
            var ex = new HttpRequestException("connect failed", cause, "POST", TEST_URI);

            assertTrue(ex.method().isPresent());
            assertEquals("POST", ex.method().get());
            assertTrue(ex.uri().isPresent());
            assertEquals(TEST_URI, ex.uri().get());
            assertEquals(cause, ex.getCause());
        }

        @Test
        void causeConstructor_usesExceptionMessageAsMessage() {
            var cause = new RuntimeException("timed out");
            var ex = new HttpRequestException(cause, "GET", TEST_URI);

            assertTrue(ex.getMessage().contains("timed out"));
        }

        @Test
        void noContextConstructor_methodAndUriAreEmpty() {
            var ex = new HttpRequestException("transport failure", new RuntimeException());

            assertTrue(ex.method().isEmpty());
            assertTrue(ex.uri().isEmpty());
        }

        @Test
        void toString_withMethodAndUri_includesBoth() {
            var ex = new HttpRequestException("connect failed", new RuntimeException(), "GET", TEST_URI);
            String s = ex.toString();

            assertTrue(s.contains("GET"));
            assertTrue(s.contains(TEST_URI.toString()));
        }

        @Test
        void toString_withoutContext_containsSimpleClassName() {
            var ex = new HttpRequestException("failure", new RuntimeException());

            assertTrue(ex.toString().contains("HttpRequestException"));
        }

        @Test
        void toString_withNullMessage_omitsMessageSection() {
            // 2-arg constructor with null message — getMessage() returns null; no method/URI
            var ex = new HttpRequestException(null, null);

            assertEquals("HttpRequestException", ex.toString());
        }

        @Test
        void toString_withBlankMessage_omitsMessageSection() {
            // 2-arg constructor with blank message — isBlank() guard prevents appending
            var ex = new HttpRequestException("   ", new RuntimeException());

            assertEquals("HttpRequestException", ex.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Transport exception subclasses
    // -------------------------------------------------------------------------

    @Nested
    class TransportExceptionSubclasses {
        @Test
        void connectException_isHttpRequestException() {
            assertInstanceOf(
                    HttpRequestException.class,
                    new ConnectException(new RuntimeException(), "GET", TEST_URI)
            );
        }

        @Test
        void connectTimeoutException_isHttpRequestException() {
            assertInstanceOf(
                    HttpRequestException.class,
                    new ConnectTimeoutException(new RuntimeException(), "GET", TEST_URI)
            );
        }

        @Test
        void readTimeoutException_isHttpRequestException() {
            assertInstanceOf(
                    HttpRequestException.class,
                    new ReadTimeoutException(new RuntimeException(), "GET", TEST_URI)
            );
        }

        @Test
        void tooManyRedirectsException_isHttpRequestException() {
            assertInstanceOf(
                    HttpRequestException.class,
                    new TooManyRedirectsException(new RuntimeException(), "GET", TEST_URI)
            );
        }

        @Test
        void abortedException_isHttpRequestException() {
            assertInstanceOf(
                    HttpRequestException.class,
                    new AbortedException(new RuntimeException(), "GET", TEST_URI)
            );
        }

        @Test
        void transportException_isHttpRequestException() {
            assertInstanceOf(
                    HttpRequestException.class,
                    new TransportException(new RuntimeException(), "GET", TEST_URI)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Transport exception — full (4-arg) constructor
    // -------------------------------------------------------------------------

    @Nested
    class TransportExceptionFullConstructor {
        private static final RuntimeException CAUSE = new RuntimeException("cause");

        @Test
        void connectException_fullConstructor_preservesMessageAndContext() {
            var ex = new ConnectException("conn refused", CAUSE, "POST", TEST_URI);

            assertEquals("conn refused", ex.getMessage());
            assertEquals(CAUSE, ex.getCause());
            assertTrue(ex.method().isPresent());
            assertTrue(ex.uri().isPresent());
        }

        @Test
        void connectTimeoutException_fullConstructor_preservesMessageAndContext() {
            var ex = new ConnectTimeoutException("timed out", CAUSE, "GET", TEST_URI);

            assertEquals("timed out", ex.getMessage());
            assertTrue(ex.method().isPresent());
        }

        @Test
        void readTimeoutException_fullConstructor_preservesMessageAndContext() {
            var ex = new ReadTimeoutException("read timed out", CAUSE, "GET", TEST_URI);

            assertEquals("read timed out", ex.getMessage());
            assertTrue(ex.method().isPresent());
        }

        @Test
        void transportException_fullConstructor_preservesMessageAndContext() {
            var ex = new TransportException("tls failure", CAUSE, "GET", TEST_URI);

            assertEquals("tls failure", ex.getMessage());
            assertTrue(ex.method().isPresent());
        }

        @Test
        void abortedException_fullConstructor_preservesMessageAndContext() {
            var ex = new AbortedException("interrupted", CAUSE, "DELETE", TEST_URI);

            assertEquals("interrupted", ex.getMessage());
            assertTrue(ex.method().isPresent());
        }

        @Test
        void tooManyRedirectsException_fullConstructor_preservesMessageAndContext() {
            var ex = new TooManyRedirectsException("too many redirects", CAUSE, "GET", TEST_URI);

            assertEquals("too many redirects", ex.getMessage());
            assertTrue(ex.method().isPresent());
        }
    }

    // -------------------------------------------------------------------------
    // Transport exception — 2-arg no-context constructor
    // -------------------------------------------------------------------------

    @Nested
    class TransportExceptionNoContextConstructor {
        private static final RuntimeException CAUSE = new RuntimeException("cause");

        @Test
        void connectException_noContextConstructor_methodAndUriAreEmpty() {
            var ex = new ConnectException("conn refused", CAUSE);

            assertTrue(ex.method().isEmpty());
            assertTrue(ex.uri().isEmpty());
        }

        @Test
        void connectTimeoutException_noContextConstructor_methodAndUriAreEmpty() {
            var ex = new ConnectTimeoutException("timed out", CAUSE);

            assertTrue(ex.method().isEmpty());
            assertTrue(ex.uri().isEmpty());
        }

        @Test
        void readTimeoutException_noContextConstructor_methodAndUriAreEmpty() {
            var ex = new ReadTimeoutException("read timed out", CAUSE);

            assertTrue(ex.method().isEmpty());
            assertTrue(ex.uri().isEmpty());
        }

        @Test
        void transportException_noContextConstructor_methodAndUriAreEmpty() {
            var ex = new TransportException("tls failure", CAUSE);

            assertTrue(ex.method().isEmpty());
            assertTrue(ex.uri().isEmpty());
        }

        @Test
        void abortedException_noContextConstructor_methodAndUriAreEmpty() {
            var ex = new AbortedException("interrupted", CAUSE);

            assertTrue(ex.method().isEmpty());
            assertTrue(ex.uri().isEmpty());
        }

        @Test
        void tooManyRedirectsException_noContextConstructor_methodAndUriAreEmpty() {
            var ex = new TooManyRedirectsException("too many redirects", CAUSE);

            assertTrue(ex.method().isEmpty());
            assertTrue(ex.uri().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // ResponseStatus
    // -------------------------------------------------------------------------

    @Nested
    class ResponseStatusTests {
        @Test
        void knownStatusCodes_resolveToCorrectEnum() {
            assertEquals(ResponseStatus.OK, ResponseStatus.fromCode(200));
            assertEquals(ResponseStatus.CREATED, ResponseStatus.fromCode(201));
            assertEquals(ResponseStatus.BAD_REQUEST, ResponseStatus.fromCode(400));
            assertEquals(ResponseStatus.NOT_FOUND, ResponseStatus.fromCode(404));
            assertEquals(ResponseStatus.INTERNAL_SERVER_ERROR, ResponseStatus.fromCode(500));
        }

        @Test
        void unknownStatusCode_resolvesToUnknown() {
            assertEquals(ResponseStatus.UNKNOWN, ResponseStatus.fromCode(999));
        }

        @Test
        void code_returnsCorrectValue() {
            assertEquals(200, ResponseStatus.OK.code());
            assertEquals(404, ResponseStatus.NOT_FOUND.code());
        }

        @Test
        void reason_isNotBlank() {
            assertFalse(ResponseStatus.OK.reason().isBlank());
            assertFalse(ResponseStatus.NOT_FOUND.reason().isBlank());
        }

        @Test
        void isSuccess_trueFor2xx_falseOtherwise() {
            assertTrue(ResponseStatus.OK.isSuccess());
            assertTrue(ResponseStatus.CREATED.isSuccess());
            assertFalse(ResponseStatus.BAD_REQUEST.isSuccess());
            assertFalse(ResponseStatus.INTERNAL_SERVER_ERROR.isSuccess());
        }

        @Test
        void isClientError_trueFor4xx_falseOtherwise() {
            assertTrue(ResponseStatus.BAD_REQUEST.isClientError());
            assertTrue(ResponseStatus.NOT_FOUND.isClientError());
            assertFalse(ResponseStatus.OK.isClientError());
            assertFalse(ResponseStatus.INTERNAL_SERVER_ERROR.isClientError());
        }

        @Test
        void isServerError_trueFor5xx_falseOtherwise() {
            assertTrue(ResponseStatus.INTERNAL_SERVER_ERROR.isServerError());
            assertTrue(ResponseStatus.SERVICE_UNAVAILABLE.isServerError());
            assertFalse(ResponseStatus.OK.isServerError());
            assertFalse(ResponseStatus.NOT_FOUND.isServerError());
        }

        /**
         * {@code UNKNOWN} has code {@code -1}.  Each range check ({@code code >= 200},
         * {@code code >= 400}, {@code code >= 500}) short-circuits to {@code false}
         * immediately, exercising the untaken branch in the {@code &&} expression.
         */
        @Test
        void unknown_isNotSuccess_isNotClientError_isNotServerError() {
            assertFalse(ResponseStatus.UNKNOWN.isSuccess());
            assertFalse(ResponseStatus.UNKNOWN.isClientError());
            assertFalse(ResponseStatus.UNKNOWN.isServerError());
        }

        @Test
        void toString_includesCodeAndReason() {
            String s = ResponseStatus.NOT_FOUND.toString();

            assertTrue(s.contains("404"));
            assertTrue(s.contains("Not Found"));
        }
    }

    // -------------------------------------------------------------------------
    // ResponseDeserializationException
    // -------------------------------------------------------------------------

    @Nested
    class ResponseDeserializationExceptionTests {
        private static final RuntimeException CAUSE = new RuntimeException("json parse error");

        @Test
        void fullConstructor_withBody_accessorsReturnCorrectValues() {
            var ex = new ResponseDeserializationException(
                    "Cannot parse response",
                    CAUSE,
                    "com.example.MyDto",
                    "{\"broken\":}"
            );

            assertEquals("Cannot parse response", ex.getMessage());
            assertEquals(CAUSE, ex.getCause());
            assertEquals("com.example.MyDto", ex.targetType());
            assertTrue(ex.rawBody().isPresent());
            assertEquals("{\"broken\":}", ex.rawBody().get());
        }

        @Test
        void fullConstructor_withNullBody_rawBodyIsEmpty() {
            var ex = new ResponseDeserializationException(
                    "Cannot parse response",
                    CAUSE,
                    "java.util.List",
                    null
            );

            assertEquals("java.util.List", ex.targetType());
            assertTrue(ex.rawBody().isEmpty());
        }

        @Test
        void targetType_isNotNull() {
            var ex = new ResponseDeserializationException("msg", CAUSE, "com.example.Foo", null);

            assertNotNull(ex.targetType());
        }
    }

    // -------------------------------------------------------------------------
    // UriSyntaxException
    // -------------------------------------------------------------------------

    @Nested
    class UriSyntaxExceptionTests {
        @Test
        void messageConstructor_messageIsAccessible() {
            var ex = new UriSyntaxException("bad template '/orders/{id}'");

            assertEquals("bad template '/orders/{id}'", ex.getMessage());
        }

        @Test
        void messageAndCauseConstructor_messageAndCauseAreAccessible() {
            var cause = new java.net.URISyntaxException("https://bad uri", "Illegal character");

            var ex = new UriSyntaxException("assembled URI is invalid", cause);

            assertEquals("assembled URI is invalid", ex.getMessage());
            assertEquals(cause, ex.getCause());
        }
    }
}

