package software.frisby.web.client;

import software.frisby.core.validation.Numbers;
import software.frisby.core.validation.Strings;
import software.frisby.core.validation.Values;

import java.net.URI;
import java.util.OptionalInt;

/**
 * Carries contextual information about a failed request execution to the configured
 * {@link RetryPolicy}, enabling the policy to make fine-grained decisions about retry
 * eligibility and delay.
 * <p>
 * A {@link RetryPolicy} implementation inspects one {@code RetryContext} per failed attempt,
 * and returns either a retry delay (via {@link java.util.Optional#of(Object)}) or
 * {@link java.util.Optional#empty()} to stop retrying.
 *
 * <h2>Context fields</h2>
 *
 * <dl>
 *   <dt>{@link #attempt()}</dt>
 *   <dd>The 1-based execution attempt number that just failed. After the initial request,
 *       {@code attempt} is 1; after the first retry it is 2, etc.</dd>
 *
 *   <dt>{@link #failure()}</dt>
 *   <dd>The exception thrown by the failed execution. Type varies by phase:
 *       <ul>
 *         <li>{@code PRE_FLIGHT}: any exception thrown by request construction (e.g.
 *             serialization error, URI resolution error, security provider error) —
 *             often not a project-specific exception type</li>
 *         <li>{@code TRANSPORT}: one of {@link software.frisby.web.client.exception.ConnectTimeoutException},
 *             {@link software.frisby.web.client.exception.ReadTimeoutException},
 *             {@link software.frisby.web.client.exception.ConnectException}, or
 *             {@link software.frisby.web.client.exception.TransportException}</li>
 *         <li>{@code HTTP_RESPONSE}: always an
 *             {@link software.frisby.web.client.exception.HttpResponseException} or subclass</li>
 *       </ul></dd>
 *
 *   <dt>{@link #method()}</dt>
 *   <dd>The HTTP method of the request (e.g. {@code "GET"}, {@code "POST"}). Always
 *       known, regardless of phase — never a placeholder or null.</dd>
 *
 *   <dt>{@link #uri()}</dt>
 *   <dd>The fully-resolved URI of the request, including any query parameters and
 *       path variables. Always known, regardless of phase — never a placeholder or null.</dd>
 *
 *   <dt>{@link #statusCode()}</dt>
 *   <dd>The HTTP response status code, present only for {@code HTTP_RESPONSE} phase failures.
 *       Empty for {@code PRE_FLIGHT} and {@code TRANSPORT} phases.</dd>
 *
 *   <dt>{@link #replayableBody()}</dt>
 *   <dd>{@code true} for requests with bodies that can be sent more than once (ordinary
 *       JSON, form-urlencoded, bodiless requests); {@code false} for streamed or
 *       non-replayable bodies (multipart form-data). Built-in policies use this to gate
 *       retry eligibility, since non-replayable bodies cannot be retried. Custom policies
 *       should check this field before deciding to retry.</dd>
 *
 *   <dt>{@link #phase()}</dt>
 *   <dd>The lifecycle phase in which the failure occurred — {@link RetryPhase#PRE_FLIGHT},
 *       {@link RetryPhase#TRANSPORT}, or {@link RetryPhase#HTTP_RESPONSE}.</dd>
 * </dl>
 *
 * <h2>Built-in policy behavior</h2>
 *
 * <p>The built-in policy (created via {@link RetryPolicy#builder()}) evaluates this context
 * in this order:
 * <ol>
 *   <li>If {@code attempt >= maxAttempts}, stop (do not retry).</li>
 *   <li>If {@code replayableBody == false}, stop.</li>
 *   <li>If the HTTP method is non-idempotent and the builder has
 *       {@code allowNonIdempotent() == false}, stop.</li>
 *   <li>If the failure/status does not match the configured {@link RetryOn} conditions, stop.</li>
 *   <li>If {@code phase == HTTP_RESPONSE} and the response contains a
 *       {@code Retry-After} header within the configured cap, use that delay.</li>
 *   <li>Otherwise, use the configured {@link RetryDelay} strategy.</li>
 * </ol>
 *
 * <h2>Custom policy usage</h2>
 *
 * <p>Advanced callers using {@link RetryPolicy#of(java.util.function.Function)} to supply
 * a custom policy gain complete control:
 *
 * <pre>{@code
 * RetryPolicy custom = RetryPolicy.of(context -> {
 *     if (context.attempt() >= 5) {
 *         return Optional.empty();  // no more than 4 retries
 *     }
 *
 *     if (!context.replayableBody()) {
 *         return Optional.empty();  // never retry non-replayable bodies
 *     }
 *
 *     String path = context.uri().getPath();
 *     if (path.startsWith("/uploads/")) {
 *         return Optional.empty();  // custom: never retry uploads
 *     }
 *
 *     if (context.statusCode().isPresent() && context.statusCode().getAsInt() == 429) {
 *         return Optional.of(Duration.ofSeconds(30));  // retry 429
 *     }
 *
 *     return Optional.empty();
 * });
 * }</pre>
 *
 * @param attempt        The 1-based execution attempt number; always {@code >= 1}.
 * @param failure        The exception thrown by the failed execution; never {@code null}.
 * @param method         The HTTP method of the request; never blank or {@code null}.
 * @param uri            The fully-resolved request URI; never {@code null}.
 * @param statusCode     The HTTP response status code, present for {@code HTTP_RESPONSE}
 *                       failures only; {@link OptionalInt#empty()} otherwise.
 * @param replayableBody {@code true} if the request body can be sent multiple times;
 *                       {@code false} for multipart or other streaming bodies.
 * @param phase          The lifecycle phase in which the failure occurred;
 *                       never {@code null}.
 * @see RetryPolicy
 * @see RetryDelay
 * @see RetryOn
 */
public record RetryContext(int attempt,
                           Throwable failure,
                           String method,
                           URI uri,
                           OptionalInt statusCode,
                           boolean replayableBody,
                           RetryPhase phase) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @param attempt        The 1-based execution attempt number; must be {@code >= 1}.
     * @param failure        The exception thrown by the failed execution; never {@code null}.
     * @param method         The HTTP method of the request; never blank or {@code null}.
     * @param uri            The fully-resolved request URI; never {@code null}.
     * @param statusCode     The HTTP response status code, present for {@code HTTP_RESPONSE}
     *                       failures only; {@link OptionalInt#empty()} otherwise.
     * @param replayableBody {@code true} if the request body can be sent multiple times;
     *                       {@code false} for multipart or other streaming bodies.
     * @param phase          The lifecycle phase in which the failure occurred; never {@code null}.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code attempt < 1}.
     * @throws software.frisby.core.validation.NullValueException                if {@code failure},
     *                                                                           {@code uri},
     *                                                                           {@code statusCode},
     *                                                                           or {@code phase}
     *                                                                           is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException               if {@code method}
     *                                                                           is blank.
     */
    public RetryContext {
        Numbers.positive("attempt", attempt);
        Strings.notBlank("method", method);
        Values.notNull("uri", uri);
        Values.notNull("failure", failure);
        Values.notNull("statusCode", statusCode);
        Values.notNull("phase", phase);
    }
}
