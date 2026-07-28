package software.frisby.web.server.event;

import software.frisby.core.validation.Values;

import java.lang.reflect.Method;

/**
 * Identifies the JAX-RS resource class and method that handled a request.
 * <p>
 * Carried by {@link RequestCompletedEvent#endpoint()} when a request was matched to
 * a resource method.  Absent for requests that did not match any resource — for
 * example, {@code 404} responses to unregistered paths or pre-flight {@code OPTIONS}
 * requests handled directly by a CORS filter.
 * <p>
 * The raw {@link Class} and {@link Method} are exposed so that consumers can format
 * the identifier however their metrics or tracing backend requires, and can read
 * annotations (e.g. {@code @Path}, {@code @GET}, or custom ones) for additional tags.
 * <p>
 * A convenience {@link #name()} method is provided for the common case of a simple
 * {@code "ClassName.methodName"} label.
 *
 * <pre>{@code
 * event.endpoint().ifPresent(ep ->
 *         counter.increment(ep.name())   // e.g. "OrdersResource.getOrder"
 * );
 * }</pre>
 *
 * @param resourceClass The JAX-RS resource class that declared the matched method.
 * @param method        The specific Java method that was invoked to handle the request.
 * @see RequestCompletedEvent#endpoint()
 */
public record Endpoint(Class<?> resourceClass, Method method) {
    /**
     * Compact constructor — validates that neither component is {@code null}.
     *
     * @param resourceClass the resource class; must not be {@code null}
     * @param method        the handler method; must not be {@code null}
     * @throws software.frisby.core.validation.NullValueException if either component
     *                                                            is {@code null}.
     */
    public Endpoint {
        Values.notNull("resourceClass", resourceClass);
        Values.notNull("method", method);
    }

    /**
     * Returns a concise {@code "ClassName.methodName"} label for this endpoint.
     * <p>
     * Suitable as a metrics tag value or log label when the full class name or
     * parameter types are not needed.
     *
     * @return A label of the form {@code "OrdersResource.getOrder"}.
     */
    public String name() {
        return resourceClass.getSimpleName() + "." + method.getName();
    }
}

