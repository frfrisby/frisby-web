package software.frisby.web.serial;

import software.frisby.core.validation.Values;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * A type token that captures a full generic type at runtime, working around Java's type erasure.
 *
 * <p>Instantiate this class as an anonymous subclass, supplying the desired type as the type
 * argument.  The constructor uses reflection to extract the actual type argument from the
 * anonymous subclass's {@code getGenericSuperclass()} metadata — information that survives
 * erasure because it is encoded in the class file, not at the call site.</p>
 *
 * <p>Pass the token to the appropriate {@code send} or {@code deserialize} method when the
 * target type is a generic container such as {@code List<Order>} or
 * {@code Map<String, User>}:</p>
 *
 * <pre>{@code
 * // Deserialize a JSON array into a typed list
 * List<Order> orders = client.get()
 *         .path("/orders")
 *         .send(new GenericType<List<Order>>() {})
 *         .body();
 *
 * // Deserialize a JSON object into a typed map
 * Map<String, User> users = client.get()
 *         .path("/users")
 *         .send(new GenericType<Map<String, User>>() {})
 *         .body();
 * }</pre>
 *
 * <p>Alternatively, declare a named subclass for a frequently-used type to avoid
 * repeating the anonymous class syntax:</p>
 *
 * <pre>{@code
 * public final class OrderListType extends GenericType<List<Order>> {}
 *
 * List<Order> orders = client.get()
 *         .path("/orders")
 *         .send(new OrderListType())
 *         .body();
 * }</pre>
 *
 * @param <T> The generic type captured by this token.
 * @implNote The super type token pattern was first described by Neal Gafter.  This
 * implementation is inspired by Jersey's {@code jakarta.ws.rs.core.GenericType}
 * (Eclipse Foundation, Apache License 2.0) and Jackson's
 * {@code com.fasterxml.jackson.core.type.TypeReference}.
 */
public abstract class GenericType<T> {
    private final Type type;
    private final Class<T> rawType;

    /**
     * Captures the generic type argument supplied by the anonymous or named subclass.
     * Must be invoked from a concrete subclass that specifies the type argument — either
     * as an anonymous class ({@code new GenericType<List<Order>>() {}}) or as a named
     * subclass ({@code class OrderListType extends GenericType<List<Order>> {}}).
     *
     * @throws IllegalStateException if the subclass does not supply a concrete type
     *                               argument for {@code T}.
     */
    @SuppressWarnings("unchecked")
    protected GenericType() {
        this.type = getTypeArgument(getClass());
        this.rawType = (Class<T>) getTypeClass(this.type);
    }

    /**
     * Creates a type token that wraps a {@link Type} instance known at runtime.
     * <p>
     * Use this constructor from framework-level integration code — such as a JAX-RS
     * {@code jakarta.ws.rs.ext.MessageBodyReader} — where the runtime type is already
     * available as a {@code java.lang.reflect.Type} and going through the anonymous-subclass
     * reflection trick of the no-arg constructor would be redundant.
     * <p>
     * Example usage inside a {@code MessageBodyReader}:
     *
     * <pre>{@code
     * public Object readFrom(Class<Object> type, Type genericType, ...) {
     *     return serializer.deserialize(body, new GenericType<>(genericType) {});
     * }
     * }</pre>
     *
     * @param type The {@link Type} to wrap; may be a {@link Class}, a
     *             {@link java.lang.reflect.ParameterizedType}, or a
     *             {@link java.lang.reflect.GenericArrayType}.
     */
    @SuppressWarnings("unchecked")
    protected GenericType(Type type) {
        this.type = Values.notNull("type", type);
        this.rawType = (Class<T>) getTypeClass(this.type);
    }

    private static Type getTypeArgument(final Class<?> clazz) {
        Type currentType;
        Class<?> currentClass = clazz;

        do {
            currentType = currentClass.getGenericSuperclass();

            if (currentType instanceof Class<?> c) {
                currentClass = c;
            }

            if (currentType instanceof ParameterizedType p) {
                currentClass = (Class<?>) p.getRawType();
            }
        } while (!currentClass.equals(GenericType.class));

        TypeVariable<?> tv = GenericType.class.getTypeParameters()[0];

        if (currentType instanceof ParameterizedType parameterizedType) {
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            int argIndex = Arrays.asList(rawType.getTypeParameters()).indexOf(tv);

            return parameterizedType.getActualTypeArguments()[argIndex];
        }

        throw new IllegalStateException(
                String.format(
                        "The type '%s' does not specify the type parameter T of GenericType<T>.",
                        currentType
                )
        );
    }

    static Class<?> getTypeClass(final Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }

        if (type instanceof ParameterizedType parameterizedType &&
                parameterizedType.getRawType() instanceof Class<?> c) {
            return c;
        }

        if (type instanceof GenericArrayType array) {
            final Class<?> componentRawType = getTypeClass(array.getGenericComponentType());

            return Array.newInstance(componentRawType, 0).getClass();
        }

        throw new IllegalArgumentException(
                String.format(
                        "Type parameter '%s' is not a class or parameterized type whose raw type is a class.",
                        type
                )
        );
    }

    /**
     * Returns the full generic type captured by this token, including any type arguments.
     * For example, for {@code new GenericType<List<String>>() {}}, this returns the
     * {@link ParameterizedType} representing {@code List<String>}.
     *
     * @return The full {@link Type} represented by this token.
     */
    public final Type type() {
        return type;
    }

    /**
     * Returns the raw (erased) class of the captured type.  For example, for
     * {@code new GenericType<List<String>>() {}}, this returns {@code List.class}.
     *
     * @return The raw {@link Class} of the captured type.
     */
    public final Class<T> rawType() {
        return rawType;
    }
}
