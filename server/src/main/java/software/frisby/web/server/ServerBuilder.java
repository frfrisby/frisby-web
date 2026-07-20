package software.frisby.web.server;

import software.frisby.core.validation.Values;
import software.frisby.web.server.event.ServerEventListener;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A builder for creating an instance of {@link Server}.
 * <p>
 * Obtain a builder via the static {@link Server#builder()} factory method, configure it,
 * and call {@link #build()} to produce a ready-to-start {@link Server}.
 *
 * <pre>{@code
 * Server server = Server.builder()
 *         .configuration(
 *                 ServerConfiguration.builder()
 *                         .port(8080)
 *                         .serializer(new JacksonSerializer())
 *                         .build()
 *         )
 *         .resources(new OrderResource(orderService))
 *         .healthCheck()
 *         .build();
 * }</pre>
 *
 * <p>Builder instances are not thread-safe and are intended to be used by a
 * single thread during application startup.</p>
 *
 * @see Server
 * @see ServerConfiguration
 */
public interface ServerBuilder {
    /**
     * Sets the configuration that controls the server's runtime behavior (port, host,
     * serializer, SSL, etc.).
     * <p>
     * Required.  {@link #build()} throws if no configuration is provided.
     *
     * @param configuration The server configuration; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code configuration} is {@code null}.
     */
    ServerBuilder configuration(ServerConfiguration configuration);

    /**
     * Convenience overload — configures the server inline via a lambda instead of
     * constructing a {@link ServerConfiguration} object explicitly.
     * <p>
     * The library creates a fresh {@link ServerConfigurationBuilder}, passes it to
     * {@code configurer}, and delegates the result to
     * {@link #configuration(ServerConfiguration)}.  This is equivalent to:
     *
     * <pre>{@code
     * ServerConfigurationBuilder builder = ServerConfiguration.builder();
     * ServerConfiguration configuration = configurer.apply(builder).build();
     * return configuration(configuration);
     * }</pre>
     * <p>
     * Typical usage:
     *
     * <pre>{@code
     * Server server = Server.builder()
     *         .configuration(c -> c
     *                 .port(8080)
     *                 .serializer(new JacksonSerializer()))
     *         .resources(new OrderResource(orderService))
     *         .healthCheck()
     *         .build();
     * }</pre>
     *
     * @param configurer A function that receives a fresh {@link ServerConfigurationBuilder}
     *                   and returns it after applying the desired settings; must not be
     *                   {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code configurer} is {@code null}.
     */
    default ServerBuilder configuration(UnaryOperator<ServerConfigurationBuilder> configurer) {
        ServerConfigurationBuilder builder = ServerConfiguration.builder();

        return configuration(
                Values.notNull("configurer", configurer)
                        .apply(builder)
                        .build()
        );
    }

    /**
     * Registers one or more JAX-RS resource instances that will handle incoming requests.
     * <p>
     * Each element must be a concrete instance of a class annotated with
     * {@code @Path}.  Required; at least one resource must be registered.
     * <p>
     * Calls are cumulative — invoking this method more than once adds to the
     * previously registered set.
     *
     * @param resources One or more JAX-RS resource instances; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code resources} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code resources} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     */
    ServerBuilder resources(Object... resources);

    /**
     * Registers one or more JAX-RS resource instances that will handle incoming requests.
     * <p>
     * Convenience overload that accepts a {@link List}.  Calls are cumulative.
     *
     * @param resources A list of JAX-RS resource instances; must not be {@code null} or empty.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code resources} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code resources} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     */
    ServerBuilder resources(List<Object> resources);

    /**
     * Registers one or more JAX-RS provider components that participate in the
     * request and response processing pipeline.
     * <p>
     * Providers are any objects annotated with {@code @Provider}, including
     * {@code ContainerRequestFilter}, {@code ContainerResponseFilter},
     * {@code ExceptionMapper}, and custom {@code MessageBodyReader} /
     * {@code MessageBodyWriter} implementations.
     * <p>
     * Calls are cumulative — invoking this method more than once adds to the
     * previously registered set.
     * <p>
     * <strong>Common opt-in components provided by this library:</strong>
     * <ul>
     *   <li>Jersey's {@code GZipEncoder} + a custom {@code ContainerResponseFilter} —
     *       for advanced compression control beyond what {@link ServerConfigurationBuilder#gzip()}
     *       provides; e.g. compressing additional media types or applying size thresholds.</li>
     * </ul>
     * <p>
     * <strong>Multipart support:</strong> To accept {@code multipart/form-data} request
     * bodies in resource methods, add {@code org.glassfish.jersey.media:jersey-media-multipart}
     * to your Maven dependencies and register {@code MultiPartFeature.class} here:
     * <pre>{@code
     * .components(org.glassfish.jersey.media.multipart.MultiPartFeature.class)
     * }</pre>
     *
     * @param components One or more JAX-RS provider component instances or classes;
     *                   must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code components} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code components} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     */
    ServerBuilder components(Object... components);

    /**
     * Registers one or more JAX-RS provider components.
     * <p>
     * Convenience overload that accepts a {@link List}.  Calls are cumulative.
     *
     * @param components A list of JAX-RS provider component instances; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code components} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code components} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     */
    ServerBuilder components(List<Object> components);

    /**
     * Mounts a built-in <em>liveness</em> health check endpoint at the default path
     * {@code /health}.
     * <p>
     * <strong>Liveness vs. readiness:</strong> This endpoint is a <em>liveness probe</em>
     * — it answers the question "Is the process alive and able to accept HTTP connections?".
     * If a liveness probe fails, the orchestrator (Kubernetes, ECS, etc.) recycles the
     * instance.  A <em>readiness probe</em> answers a different question: "Is this instance
     * ready to receive production traffic — are its downstream dependencies healthy?"
     * Readiness is application-specific (database connections, caches, queues) and must be
     * implemented by the caller as a regular JAX-RS resource registered via
     * {@link #resources(Object...)}.
     * <p>
     * <strong>Endpoint contract:</strong> Accepts {@code GET} requests and always returns
     * HTTP 200 with the body:
     * <pre>{@code {"status":"UP"}}</pre>
     * <p>
     * <strong>Concurrency gate:</strong> This endpoint bypasses the
     * {@link ServerConfigurationBuilder#maxConcurrentRequests(int) maxConcurrentRequests}
     * semaphore when the server is healthy but at capacity.  Under high load every permit
     * may be held by real requests; rejecting the health check with 503 in that case would
     * trick the load balancer into recycling a live, healthy instance — the opposite of the
     * intended behavior.  During graceful shutdown, the health check returns 503 just like
     * all other requests — that is the correct drain signal to the load balancer to stop
     * routing traffic to this instance while in-flight requests complete.
     * <p>
     * Health check requests are logged at {@code TRACE} rather than {@code INFO} — they
     * are typically polled every few seconds by a load balancer and would otherwise drown
     * out meaningful request logs.
     * <p>
     * {@link software.frisby.web.server.event.ServerEventListener} callbacks are suppressed
     * for health check requests so that high-frequency polling does not inflate application
     * metrics.
     * <p>
     * Use {@link #healthCheck(String)} to specify a custom path (e.g. {@code /readyz} for
     * Kubernetes liveness probes).
     *
     * @return This builder.
     */
    ServerBuilder healthCheck();

    /**
     * Mounts a built-in <em>liveness</em> health check endpoint at the given path.
     * <p>
     * Behaves identically to {@link #healthCheck()} except that the endpoint is mounted at
     * {@code path} instead of the default {@code /health}.  All liveness / readiness
     * semantics and concurrency gate behavior described in {@link #healthCheck()} apply
     * equally here.
     *
     * <pre>{@code
     * .healthCheck("/readyz")   // Kubernetes liveness probe
     * }</pre>
     *
     * @param path The context path for the health check endpoint; must not be blank, must
     *             start with {@code /}, must not exceed 256 characters, may contain up to
     *             64 path segments, and each segment may contain only letters, digits,
     *             hyphens, underscores, and dots.  Trailing slashes, consecutive slashes,
     *             whitespace, and URI-special characters ({@code #}, {@code ?}, etc.) are
     *             rejected.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException                if {@code path} is {@code null}.
     * @throws software.frisby.core.validation.BlankValueException               if {@code path} is blank.
     * @throws software.frisby.core.validation.StringLengthOutsideRangeException if {@code path} exceeds
     *                                                                           256 characters.
     * @throws software.frisby.core.validation.PatternMismatchException          if {@code path} does not
     *                                                                           start with {@code /},
     *                                                                           ends with {@code /},
     *                                                                           contains consecutive
     *                                                                           slashes, whitespace, or
     *                                                                           URI-special characters.
     */
    ServerBuilder healthCheck(String path);

    /**
     * Registers one or more {@link AuthenticationProvider} instances that will authenticate
     * incoming requests.
     * <p>
     * Providers are tried in insertion order using a <em>first-accepts-wins</em> strategy.
     * The first provider whose {@link AuthenticationProvider#accepts accepts()} returns
     * {@code true} authenticates the request.  If no provider accepts, a
     * {@code 401 Unauthorized} response is returned immediately.
     * <p>
     * Calls are cumulative — invoking this method more than once appends to the previously
     * registered set.  The health check endpoint (if configured via {@link #healthCheck()})
     * always bypasses authentication.
     *
     * <h4>Public (unsecured) endpoints alongside secured ones</h4>
     * <p>
     * Authentication is applied globally to all requests that are not the health check
     * endpoint.  To expose some endpoints without credentials while protecting others,
     * add an <em>anonymous catch-all provider</em> as the last entry in the chain.  It
     * should always {@code accept()} any request and return an anonymous
     * {@link ServerSecurityContext}.  Endpoint-level access control is then enforced by
     * {@code @RolesAllowed} on the resource methods that require a real identity:
     *
     * <pre>{@code
     * .authentication(
     *     BearerTokenAuthenticationProvider.of(jwtService::validate),
     *     // Fallback — unauthenticated requests receive an anonymous principal.
     *     // Protected methods must declare @RolesAllowed to reject anonymous callers.
     *     context -> true,
     *     context -> ServerSecurityContext.of(() -> "anonymous")
     * )
     * .components(RolesAllowedDynamicFeature.class)
     * }</pre>
     *
     * <h4>Role-based access control</h4>
     * <p>
     * To use {@code @RolesAllowed} on resource methods, register Jersey's
     * {@code RolesAllowedDynamicFeature} as a component and return a
     * {@link ServerSecurityContext} with a roles set from
     * {@link AuthenticationProvider#authenticate}:
     * <pre>{@code
     * .authentication(
     *     BasicAuthAuthenticationProvider.of((username, password) ->
     *         ServerSecurityContext.of(
     *             userService.authenticate(username, password),
     *             Set.of(user.role().name())
     *         )
     *     )
     * )
     * .components(RolesAllowedDynamicFeature.class)
     * }</pre>
     *
     * @param providers One or more authentication providers; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code providers} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code providers} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     */
    ServerBuilder authentication(AuthenticationProvider... providers);

    /**
     * Registers one or more {@link AuthenticationProvider} instances.
     * <p>
     * Convenience overload that accepts a {@link List}.  Calls are cumulative.
     *
     * @param providers A list of authentication providers; must not be {@code null} or empty.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException       if {@code providers} is {@code null}.
     * @throws software.frisby.core.validation.MissingElementsException if {@code providers} is empty.
     * @throws software.frisby.core.validation.NullElementException     if any element is {@code null}.
     */
    ServerBuilder authentication(List<AuthenticationProvider> providers);

    /**
     * Sets the {@link ServerEventListener} that will receive notifications after each
     * request completes or fails.
     * <p>
     * Optional.  When omitted, a no-op listener is used and no overhead is incurred.
     *
     * @param eventListener The event listener; must not be {@code null}.
     * @return This builder.
     * @throws software.frisby.core.validation.NullValueException if {@code eventListener} is {@code null}.
     */
    ServerBuilder eventListener(ServerEventListener eventListener);

    /**
     * Returns a new {@link Server} instance configured from the options supplied to
     * this builder.
     *
     * @return A new, not-yet-started {@link Server}; never {@code null}.
     * @throws IllegalStateException if no {@link #configuration(ServerConfiguration) configuration}
     *                               has been set, or no
     *                               {@link #resources(Object...) resources} have been registered.
     */
    Server build();
}

