package software.frisby.web.server;

import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.DurationOutsideRangeException;
import software.frisby.core.validation.MissingElementsException;
import software.frisby.core.validation.NullValueException;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpErrors}.
 * <p>
 * {@code BadRequest} covers all six overloads in full — status, body, content-type,
 * and cause — since every status code shares the same three private helpers.
 * {@code Unauthorized} verifies the WWW-Authenticate suppression.
 * {@code StatusCodes} sweeps all remaining status code methods with the no-arg overload.
 */
class HttpErrorsTest {

    // -------------------------------------------------------------------------
    // badRequest — full overload coverage
    // -------------------------------------------------------------------------

    @Nested
    class BadRequest {
        @Test
        void noBody_status400_noEntity() {
            WebApplicationException ex = HttpErrors.badRequest();

            assertEquals(400, ex.getResponse().getStatus());
            assertNull(ex.getResponse().getEntity());
            assertNull(ex.getCause());
        }

        @Test
        void stringBody_status400_textPlain() {
            WebApplicationException ex = HttpErrors.badRequest("'name' must not be blank");

            assertEquals(400, ex.getResponse().getStatus());
            assertEquals(MediaType.TEXT_PLAIN_TYPE, ex.getResponse().getMediaType());
            assertEquals("'name' must not be blank", ex.getResponse().getEntity());
        }

        @Test
        void objectBody_status400_applicationJson() {
            record ErrorBody(String message) {
            }
            ErrorBody body = new ErrorBody("invalid");

            WebApplicationException ex = HttpErrors.badRequest(body);

            assertEquals(400, ex.getResponse().getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, ex.getResponse().getMediaType());
            assertSame(body, ex.getResponse().getEntity());
        }

        @Test
        void throwableOnly_status400_causeAttached_noEntity() {
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.badRequest(cause);

            assertEquals(400, ex.getResponse().getStatus());
            assertSame(cause, ex.getCause());
            assertNull(ex.getResponse().getEntity());
        }

        @Test
        void stringAndThrowable_status400_textPlainBodyAndCause() {
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.badRequest("'name' must not be blank", cause);

            assertEquals(400, ex.getResponse().getStatus());
            assertEquals(MediaType.TEXT_PLAIN_TYPE, ex.getResponse().getMediaType());
            assertEquals("'name' must not be blank", ex.getResponse().getEntity());
            assertSame(cause, ex.getCause());
        }

        @Test
        void objectAndThrowable_status400_jsonBodyAndCause() {
            record ErrorBody(String message) {
            }
            ErrorBody body = new ErrorBody("invalid");
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.badRequest(body, cause);

            assertEquals(400, ex.getResponse().getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, ex.getResponse().getMediaType());
            assertSame(body, ex.getResponse().getEntity());
            assertSame(cause, ex.getCause());
        }
    }

    // -------------------------------------------------------------------------
    // unauthorized — WWW-Authenticate suppression
    // -------------------------------------------------------------------------

    @Nested
    class Unauthorized {
        @Test
        void noBody_status401_noWwwAuthenticateHeader() {
            WebApplicationException ex = HttpErrors.unauthorized();

            assertEquals(401, ex.getResponse().getStatus());
            assertFalse(ex.getResponse().getHeaders().containsKey("WWW-Authenticate"));
        }

        @Test
        void stringBody_status401_noWwwAuthenticateHeader() {
            WebApplicationException ex = HttpErrors.unauthorized("Token has expired.");

            assertEquals(401, ex.getResponse().getStatus());
            assertFalse(ex.getResponse().getHeaders().containsKey("WWW-Authenticate"));
            assertEquals("Token has expired.", ex.getResponse().getEntity());
        }

        @Test
        void objectBody_status401_noWwwAuthenticateHeader() {
            record AuthError(String reason) {
            }

            WebApplicationException ex = HttpErrors.unauthorized(new AuthError("expired"));

            assertEquals(401, ex.getResponse().getStatus());
            assertFalse(ex.getResponse().getHeaders().containsKey("WWW-Authenticate"));
        }

        @Test
        void throwable_status401_noWwwAuthenticateHeader() {
            WebApplicationException ex = HttpErrors.unauthorized(new RuntimeException("cause"));

            assertEquals(401, ex.getResponse().getStatus());
            assertFalse(ex.getResponse().getHeaders().containsKey("WWW-Authenticate"));
        }

        @Test
        void stringAndThrowable_status401_noWwwAuthenticateHeader() {
            RuntimeException cause = new RuntimeException("cause");

            WebApplicationException ex = HttpErrors.unauthorized("Token has expired.", cause);

            assertEquals(401, ex.getResponse().getStatus());
            assertFalse(ex.getResponse().getHeaders().containsKey("WWW-Authenticate"));
        }

        @Test
        void objectAndThrowable_status401_noWwwAuthenticateHeader() {
            record AuthError(String reason) {
            }
            RuntimeException cause = new RuntimeException("cause");

            WebApplicationException ex = HttpErrors.unauthorized(new AuthError("expired"), cause);

            assertEquals(401, ex.getResponse().getStatus());
            assertFalse(ex.getResponse().getHeaders().containsKey("WWW-Authenticate"));
        }
    }

    // -------------------------------------------------------------------------
    // retryAfter parameter validation — shared helpers, tested once
    // -------------------------------------------------------------------------

    @Nested
    class RetryAfterValidation {
        @Test
        void nullRetryAfter_throwsNullValueException() {
            assertThrows(NullValueException.class, () -> HttpErrors.tooManyRequests((Duration) null));
        }

        @Test
        void negativeRetryAfter_throwsDurationOutsideRangeException() {
            assertThrows(DurationOutsideRangeException.class,
                    () -> HttpErrors.tooManyRequests(Duration.ofSeconds(-1)));
        }

        @Test
        void zeroRetryAfter_isAllowed() {
            WebApplicationException ex = HttpErrors.tooManyRequests(Duration.ZERO);

            assertEquals(429, ex.getResponse().getStatus());
            assertEquals("0", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
        }

        @Test
        void nullRetryAfterWithMessage_throwsNullValueException() {
            assertThrows(NullValueException.class,
                    () -> HttpErrors.tooManyRequests("msg", (Duration) null));
        }

        @Test
        void negativeRetryAfterWithMessage_throwsDurationOutsideRangeException() {
            assertThrows(DurationOutsideRangeException.class,
                    () -> HttpErrors.tooManyRequests("msg", Duration.ofSeconds(-1)));
        }

        @Test
        void nullRetryAfterWithBody_throwsNullValueException() {
            assertThrows(NullValueException.class,
                    () -> HttpErrors.tooManyRequests(new Object(), (Duration) null));
        }
    }

    // -------------------------------------------------------------------------
    // Status codes — no-arg sweep for all remaining methods
    // -------------------------------------------------------------------------

    @Nested
    class StatusCodes {
        @Test
        void forbidden_returns403() {
            assertEquals(403, HttpErrors.forbidden().getResponse().getStatus());
        }

        @Test
        void notFound_returns404() {
            assertEquals(404, HttpErrors.notFound().getResponse().getStatus());
        }

        @Test
        void methodNotAllowed_returns405() {
            NotAllowedException ex = HttpErrors.methodNotAllowed(HttpVerb.GET);

            assertEquals(405, ex.getResponse().getStatus());
            assertEquals(List.of("GET"), ex.getResponse().getHeaders().get("Allow"));
        }

        @Test
        void notAcceptable_returns406() {
            assertEquals(406, HttpErrors.notAcceptable().getResponse().getStatus());
        }

        @Test
        void requestTimeout_returns408() {
            assertEquals(408, HttpErrors.requestTimeout().getResponse().getStatus());
        }

        @Test
        void conflict_returns409() {
            assertEquals(409, HttpErrors.conflict().getResponse().getStatus());
        }

        @Test
        void gone_returns410() {
            assertEquals(410, HttpErrors.gone().getResponse().getStatus());
        }

        @Test
        void payloadTooLarge_returns413() {
            assertEquals(413, HttpErrors.payloadTooLarge().getResponse().getStatus());
        }

        @Test
        void unsupportedMediaType_returns415() {
            assertEquals(415, HttpErrors.unsupportedMediaType().getResponse().getStatus());
        }

        @Test
        void unprocessableEntity_returns422() {
            assertEquals(422, HttpErrors.unprocessableEntity().getResponse().getStatus());
        }

        @Test
        void tooManyRequests_returns429() {
            assertEquals(429, HttpErrors.tooManyRequests().getResponse().getStatus());
        }

        @Test
        void internalServerError_returns500() {
            assertEquals(500, HttpErrors.internalServerError().getResponse().getStatus());
        }

        @Test
        void notImplemented_returns501() {
            assertEquals(501, HttpErrors.notImplemented().getResponse().getStatus());
        }

        @Test
        void badGateway_returns502() {
            assertEquals(502, HttpErrors.badGateway().getResponse().getStatus());
        }

        @Test
        void serviceUnavailable_returns503() {
            assertEquals(503, HttpErrors.serviceUnavailable().getResponse().getStatus());
        }

        @Test
        void gatewayTimeout_returns504() {
            assertEquals(504, HttpErrors.gatewayTimeout().getResponse().getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Remaining statuses — non-no-arg overloads (status verified; body/cause
    // behaviour is fully proven by the BadRequest suite above)
    // -------------------------------------------------------------------------

    @Nested
    class Forbidden {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns403() {
            assertEquals(403, HttpErrors.forbidden("msg").getResponse().getStatus());
        }

        @Test
        void object_returns403() {
            assertEquals(403, HttpErrors.forbidden(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns403() {
            assertEquals(403, HttpErrors.forbidden(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns403() {
            assertEquals(403, HttpErrors.forbidden("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns403() {
            assertEquals(403, HttpErrors.forbidden(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class NotFound {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns404() {
            assertEquals(404, HttpErrors.notFound("msg").getResponse().getStatus());
        }

        @Test
        void object_returns404() {
            assertEquals(404, HttpErrors.notFound(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns404() {
            assertEquals(404, HttpErrors.notFound(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns404() {
            assertEquals(404, HttpErrors.notFound("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns404() {
            assertEquals(404, HttpErrors.notFound(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class MethodNotAllowed {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void noBody_returns405() {
            NotAllowedException ex = HttpErrors.methodNotAllowed(HttpVerb.GET, HttpVerb.PUT, HttpVerb.PUT);

            assertNull(ex.getCause());
            assertEquals(405, ex.getResponse().getStatus());

            Set<String> methods = Set.of(ex.getResponse().getHeaders().getFirst("Allow").toString().split(", "));
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("PUT"));

            assertNull(ex.getResponse().getEntity());
        }

        @Test
        void string_returns405() {
            NotAllowedException ex = HttpErrors.methodNotAllowed("msg", HttpVerb.GET, HttpVerb.PUT);

            assertEquals("msg", ex.getMessage());
            assertNull(ex.getCause());
            assertEquals(405, ex.getResponse().getStatus());

            Set<String> methods = Set.of(ex.getResponse().getHeaders().getFirst("Allow").toString().split(", "));
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("PUT"));

            assertEquals("msg", ex.getResponse().getEntity());
            assertEquals(MediaType.TEXT_PLAIN_TYPE, ex.getResponse().getMediaType());
        }

        @Test
        void object_returns405() {
            NotAllowedException ex = HttpErrors.methodNotAllowed(BODY, HttpVerb.GET, HttpVerb.PUT);

            assertNull(ex.getCause());
            assertEquals(405, ex.getResponse().getStatus());

            Set<String> methods = Set.of(ex.getResponse().getHeaders().getFirst("Allow").toString().split(", "));
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("PUT"));

            assertEquals(BODY, ex.getResponse().getEntity());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, ex.getResponse().getMediaType());
        }

        @Test
        void throwable_returns405() {
            NotAllowedException ex = HttpErrors.methodNotAllowed(CAUSE, HttpVerb.GET, HttpVerb.PUT);

            assertEquals(CAUSE, ex.getCause());
            assertEquals(405, ex.getResponse().getStatus());

            Set<String> methods = Set.of(ex.getResponse().getHeaders().getFirst("Allow").toString().split(", "));
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("PUT"));

            assertNull(ex.getResponse().getEntity());
        }

        @Test
        void stringAndThrowable_returns405() {
            NotAllowedException ex = HttpErrors.methodNotAllowed("msg", CAUSE, HttpVerb.GET, HttpVerb.PUT);

            assertEquals("msg", ex.getMessage());
            assertEquals(CAUSE, ex.getCause());
            assertEquals(405, ex.getResponse().getStatus());

            Set<String> methods = Set.of(ex.getResponse().getHeaders().getFirst("Allow").toString().split(", "));
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("PUT"));

            assertEquals("msg", ex.getResponse().getEntity());
            assertEquals(MediaType.TEXT_PLAIN_TYPE, ex.getResponse().getMediaType());
        }

        @Test
        void objectAndThrowable_returns405() {
            NotAllowedException ex = HttpErrors.methodNotAllowed(BODY, CAUSE, HttpVerb.GET, HttpVerb.PUT);

            assertEquals(CAUSE, ex.getCause());
            assertEquals(405, ex.getResponse().getStatus());

            Set<String> methods = Set.of(ex.getResponse().getHeaders().getFirst("Allow").toString().split(", "));
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("PUT"));

            assertEquals(BODY, ex.getResponse().getEntity());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, ex.getResponse().getMediaType());
        }

        @Test
        void duplicateMethods_areDeduped() {
            NotAllowedException ex = HttpErrors.methodNotAllowed(HttpVerb.GET, HttpVerb.PUT, HttpVerb.GET);

            String allowHeader = ex.getResponse().getHeaders().getFirst("Allow").toString();
            Set<String> methods = Set.of(allowHeader.split(", "));
            assertEquals(2, methods.size());
            assertTrue(methods.contains("GET"));
            assertTrue(methods.contains("PUT"));
        }

        @Test
        void nullMethods_throwsNullValueException() {
            assertThrows(
                    NullValueException.class,
                    () -> HttpErrors.methodNotAllowed((HttpVerb[]) null)
            );
        }

        @Test
        void emptyMethods_throwsMissingElementsException() {
            assertThrows(
                    MissingElementsException.class,
                    () -> HttpErrors.methodNotAllowed()
            );
        }
    }

    @Nested
    class NotAcceptable {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns406() {
            assertEquals(406, HttpErrors.notAcceptable("msg").getResponse().getStatus());
        }

        @Test
        void object_returns406() {
            assertEquals(406, HttpErrors.notAcceptable(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns406() {
            assertEquals(406, HttpErrors.notAcceptable(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns406() {
            assertEquals(406, HttpErrors.notAcceptable("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns406() {
            assertEquals(406, HttpErrors.notAcceptable(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class RequestTimeout {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns408() {
            assertEquals(408, HttpErrors.requestTimeout("msg").getResponse().getStatus());
        }

        @Test
        void object_returns408() {
            assertEquals(408, HttpErrors.requestTimeout(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns408() {
            assertEquals(408, HttpErrors.requestTimeout(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns408() {
            assertEquals(408, HttpErrors.requestTimeout("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns408() {
            assertEquals(408, HttpErrors.requestTimeout(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class Conflict {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns409() {
            assertEquals(409, HttpErrors.conflict("msg").getResponse().getStatus());
        }

        @Test
        void object_returns409() {
            assertEquals(409, HttpErrors.conflict(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns409() {
            assertEquals(409, HttpErrors.conflict(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns409() {
            assertEquals(409, HttpErrors.conflict("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns409() {
            assertEquals(409, HttpErrors.conflict(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class Gone {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns410() {
            assertEquals(410, HttpErrors.gone("msg").getResponse().getStatus());
        }

        @Test
        void object_returns410() {
            assertEquals(410, HttpErrors.gone(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns410() {
            assertEquals(410, HttpErrors.gone(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns410() {
            assertEquals(410, HttpErrors.gone("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns410() {
            assertEquals(410, HttpErrors.gone(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class PayloadTooLarge {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns413() {
            assertEquals(413, HttpErrors.payloadTooLarge("msg").getResponse().getStatus());
        }

        @Test
        void object_returns413() {
            assertEquals(413, HttpErrors.payloadTooLarge(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns413() {
            assertEquals(413, HttpErrors.payloadTooLarge(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns413() {
            assertEquals(413, HttpErrors.payloadTooLarge("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns413() {
            assertEquals(413, HttpErrors.payloadTooLarge(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class UnsupportedMediaType {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns415() {
            assertEquals(415, HttpErrors.unsupportedMediaType("msg").getResponse().getStatus());
        }

        @Test
        void object_returns415() {
            assertEquals(415, HttpErrors.unsupportedMediaType(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns415() {
            assertEquals(415, HttpErrors.unsupportedMediaType(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns415() {
            assertEquals(415, HttpErrors.unsupportedMediaType("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns415() {
            assertEquals(415, HttpErrors.unsupportedMediaType(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class UnprocessableEntity {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns422() {
            assertEquals(422, HttpErrors.unprocessableEntity("msg").getResponse().getStatus());
        }

        @Test
        void object_returns422() {
            assertEquals(422, HttpErrors.unprocessableEntity(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns422() {
            assertEquals(422, HttpErrors.unprocessableEntity(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns422() {
            assertEquals(422, HttpErrors.unprocessableEntity("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns422() {
            assertEquals(422, HttpErrors.unprocessableEntity(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class TooManyRequests {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns429() {
            assertEquals(429, HttpErrors.tooManyRequests("msg").getResponse().getStatus());
        }

        @Test
        void object_returns429() {
            assertEquals(429, HttpErrors.tooManyRequests(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns429() {
            assertEquals(429, HttpErrors.tooManyRequests(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns429() {
            assertEquals(429, HttpErrors.tooManyRequests("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns429() {
            assertEquals(429, HttpErrors.tooManyRequests(BODY, CAUSE).getResponse().getStatus());
        }

        @Test
        void retryAfterOnly_returns429WithHeader() {
            WebApplicationException ex = HttpErrors.tooManyRequests(Duration.ofSeconds(30));

            assertEquals(429, ex.getResponse().getStatus());
            assertEquals("30", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
            assertNull(ex.getResponse().getEntity());
        }

        @Test
        void stringAndRetryAfter_returns429WithBodyAndHeader() {
            WebApplicationException ex = HttpErrors.tooManyRequests("Rate limit exceeded.", Duration.ofSeconds(30));

            assertEquals(429, ex.getResponse().getStatus());
            assertEquals(MediaType.TEXT_PLAIN_TYPE, ex.getResponse().getMediaType());
            assertEquals("Rate limit exceeded.", ex.getResponse().getEntity());
            assertEquals("30", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
        }

        @Test
        void objectAndRetryAfter_returns429WithJsonBodyAndHeader() {
            record RateLimitBody(String message) {
            }
            RateLimitBody body = new RateLimitBody("Rate limit exceeded.");

            WebApplicationException ex = HttpErrors.tooManyRequests(body, Duration.ofSeconds(30));

            assertEquals(429, ex.getResponse().getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, ex.getResponse().getMediaType());
            assertSame(body, ex.getResponse().getEntity());
            assertEquals("30", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
        }

        @Test
        void retryAfterAndCause_returns429WithHeaderAndCause() {
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.tooManyRequests(Duration.ofSeconds(30), cause);

            assertEquals(429, ex.getResponse().getStatus());
            assertEquals("30", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
            assertSame(cause, ex.getCause());
        }

        @Test
        void stringAndRetryAfterAndCause_returns429WithBodyHeaderAndCause() {
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.tooManyRequests("Rate limit exceeded.", Duration.ofSeconds(30), cause);

            assertEquals(429, ex.getResponse().getStatus());
            assertEquals("Rate limit exceeded.", ex.getResponse().getEntity());
            assertEquals("30", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
            assertSame(cause, ex.getCause());
        }

        @Test
        void objectAndRetryAfterAndCause_returns429WithJsonBodyHeaderAndCause() {
            record RateLimitBody(String message) {
            }
            RateLimitBody body = new RateLimitBody("Rate limit exceeded.");
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.tooManyRequests(body, Duration.ofSeconds(30), cause);

            assertEquals(429, ex.getResponse().getStatus());
            assertSame(body, ex.getResponse().getEntity());
            assertEquals("30", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    class InternalServerError {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns500() {
            assertEquals(500, HttpErrors.internalServerError("msg").getResponse().getStatus());
        }

        @Test
        void object_returns500() {
            assertEquals(500, HttpErrors.internalServerError(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns500() {
            assertEquals(500, HttpErrors.internalServerError(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns500() {
            assertEquals(500, HttpErrors.internalServerError("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns500() {
            assertEquals(500, HttpErrors.internalServerError(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class NotImplemented {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns501() {
            assertEquals(501, HttpErrors.notImplemented("msg").getResponse().getStatus());
        }

        @Test
        void object_returns501() {
            assertEquals(501, HttpErrors.notImplemented(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns501() {
            assertEquals(501, HttpErrors.notImplemented(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns501() {
            assertEquals(501, HttpErrors.notImplemented("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns501() {
            assertEquals(501, HttpErrors.notImplemented(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class BadGateway {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns502() {
            assertEquals(502, HttpErrors.badGateway("msg").getResponse().getStatus());
        }

        @Test
        void object_returns502() {
            assertEquals(502, HttpErrors.badGateway(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns502() {
            assertEquals(502, HttpErrors.badGateway(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns502() {
            assertEquals(502, HttpErrors.badGateway("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns502() {
            assertEquals(502, HttpErrors.badGateway(BODY, CAUSE).getResponse().getStatus());
        }
    }

    @Nested
    class ServiceUnavailable {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns503() {
            assertEquals(503, HttpErrors.serviceUnavailable("msg").getResponse().getStatus());
        }

        @Test
        void object_returns503() {
            assertEquals(503, HttpErrors.serviceUnavailable(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns503() {
            assertEquals(503, HttpErrors.serviceUnavailable(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns503() {
            assertEquals(503, HttpErrors.serviceUnavailable("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns503() {
            assertEquals(503, HttpErrors.serviceUnavailable(BODY, CAUSE).getResponse().getStatus());
        }

        @Test
        void retryAfterOnly_returns503WithHeader() {
            WebApplicationException ex = HttpErrors.serviceUnavailable(Duration.ofSeconds(60));

            assertEquals(503, ex.getResponse().getStatus());
            assertEquals("60", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
            assertNull(ex.getResponse().getEntity());
        }

        @Test
        void stringAndRetryAfter_returns503WithBodyAndHeader() {
            WebApplicationException ex = HttpErrors.serviceUnavailable("Key service down.", Duration.ofSeconds(60));

            assertEquals(503, ex.getResponse().getStatus());
            assertEquals(MediaType.TEXT_PLAIN_TYPE, ex.getResponse().getMediaType());
            assertEquals("Key service down.", ex.getResponse().getEntity());
            assertEquals("60", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
        }

        @Test
        void objectAndRetryAfter_returns503WithJsonBodyAndHeader() {
            record ServiceErrorBody(String message) {
            }
            ServiceErrorBody body = new ServiceErrorBody("Key service down.");

            WebApplicationException ex = HttpErrors.serviceUnavailable(body, Duration.ofSeconds(60));

            assertEquals(503, ex.getResponse().getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, ex.getResponse().getMediaType());
            assertSame(body, ex.getResponse().getEntity());
            assertEquals("60", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
        }

        @Test
        void retryAfterAndCause_returns503WithHeaderAndCause() {
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.serviceUnavailable(Duration.ofSeconds(60), cause);

            assertEquals(503, ex.getResponse().getStatus());
            assertEquals("60", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
            assertSame(cause, ex.getCause());
        }

        @Test
        void stringAndRetryAfterAndCause_returns503WithBodyHeaderAndCause() {
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.serviceUnavailable("Key service down.", Duration.ofSeconds(60), cause);

            assertEquals(503, ex.getResponse().getStatus());
            assertEquals("Key service down.", ex.getResponse().getEntity());
            assertEquals("60", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
            assertSame(cause, ex.getCause());
        }

        @Test
        void objectAndRetryAfterAndCause_returns503WithJsonBodyHeaderAndCause() {
            record ServiceErrorBody(String message) {
            }
            ServiceErrorBody body = new ServiceErrorBody("Key service down.");
            RuntimeException cause = new RuntimeException("upstream");

            WebApplicationException ex = HttpErrors.serviceUnavailable(body, Duration.ofSeconds(60), cause);

            assertEquals(503, ex.getResponse().getStatus());
            assertSame(body, ex.getResponse().getEntity());
            assertEquals("60", ex.getResponse().getHeaders().getFirst("Retry-After").toString());
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    class GatewayTimeout {
        private static final RuntimeException CAUSE = new RuntimeException("upstream");
        private static final Object BODY = new Object();

        @Test
        void string_returns504() {
            assertEquals(504, HttpErrors.gatewayTimeout("msg").getResponse().getStatus());
        }

        @Test
        void object_returns504() {
            assertEquals(504, HttpErrors.gatewayTimeout(BODY).getResponse().getStatus());
        }

        @Test
        void throwable_returns504() {
            assertEquals(504, HttpErrors.gatewayTimeout(CAUSE).getResponse().getStatus());
        }

        @Test
        void stringAndThrowable_returns504() {
            assertEquals(504, HttpErrors.gatewayTimeout("msg", CAUSE).getResponse().getStatus());
        }

        @Test
        void objectAndThrowable_returns504() {
            assertEquals(504, HttpErrors.gatewayTimeout(BODY, CAUSE).getResponse().getStatus());
        }
    }
}

