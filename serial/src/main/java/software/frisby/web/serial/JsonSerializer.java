package software.frisby.web.serial;

/**
 * A pluggable interface for serializing objects to JSON and deserializing JSON back to objects.
 * <p>
 * Implement this interface using the JSON library of your choice (Jackson, Gson, etc.)
 * and register it with the builder of the module you are using — for example,
 * the HTTP client's {@code ConfigurationBuilder} or the HTTP server's
 * {@code ServerConfigurationBuilder}.
 * <p>
 * No default implementation is provided.  The respective module's builder throws if
 * none is registered.
 * <p>
 * All {@code byte[]} content is treated as UTF-8 encoded JSON, as required by
 * <a href="https://www.rfc-editor.org/rfc/rfc8259#section-8.1">RFC 8259 §8.1</a>.
 * Implementations must encode serialized output as UTF-8 and must be able to decode
 * UTF-8 encoded input.
 *
 * <pre>{@code
 * // Example: Jackson (zero intermediate allocation)
 * public final class JacksonSerializer implements JsonSerializer {
 *     private static final ObjectMapper MAPPER = new ObjectMapper();
 *
 *     @Override
 *     public byte[] serialize(Object value) {
 *         try {
 *             return MAPPER.writeValueAsBytes(value);
 *         } catch (JsonProcessingException e) {
 *             throw new IllegalArgumentException("Serialization failed.", e);
 *         }
 *     }
 *
 *     @Override
 *     public <T> T deserialize(byte[] content, Class<T> type) {
 *         try {
 *             return MAPPER.readValue(content, type);
 *         } catch (JsonProcessingException e) {
 *             throw new IllegalArgumentException("Deserialization failed.", e);
 *         }
 *     }
 *
 *     @Override
 *     public <T> T deserialize(byte[] content, GenericType<T> genericType) {
 *         try {
 *             JavaType javaType = MAPPER.getTypeFactory()
 *                     .constructType(genericType.type());
 *             return MAPPER.readValue(content, javaType);
 *         } catch (JsonProcessingException e) {
 *             throw new IllegalArgumentException("Deserialization failed.", e);
 *         }
 *     }
 * }
 *
 * // Example: Gson (explicit UTF-8 encoding)
 * public final class GsonSerializer implements JsonSerializer {
 *     private static final Gson GSON = new Gson();
 *
 *     @Override
 *     public byte[] serialize(Object value) {
 *         return GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
 *     }
 *
 *     @Override
 *     public <T> T deserialize(byte[] content, Class<T> type) {
 *         return GSON.fromJson(new String(content, StandardCharsets.UTF_8), type);
 *     }
 *
 *     @Override
 *     public <T> T deserialize(byte[] content, GenericType<T> genericType) {
 *         Type rawType = genericType.type();
 *         return GSON.fromJson(new String(content, StandardCharsets.UTF_8), rawType);
 *     }
 * }
 * }</pre>
 */
public interface JsonSerializer {
    /**
     * Serializes the provided value to a UTF-8 encoded JSON byte array.
     *
     * @param value The object to serialize; must not be {@code null}.
     * @return A UTF-8 encoded JSON representation of {@code value}.
     * @throws IllegalArgumentException if serialization fails.
     */
    byte[] serialize(Object value);

    /**
     * Deserializes the provided UTF-8 encoded JSON byte array to an instance of the
     * specified type.
     *
     * @param content The UTF-8 encoded JSON bytes to deserialize; must not be {@code null}.
     * @param type    The target type; must not be {@code null}.
     * @param <T>     The target type.
     * @return A new instance of {@code T} populated from the JSON content.
     * @throws IllegalArgumentException if deserialization fails.
     */
    <T> T deserialize(byte[] content, Class<T> type);

    /**
     * Deserializes the provided UTF-8 encoded JSON byte array to an instance of the
     * specified generic type.
     * <p>
     * Use this overload when the target type is a generic container such as
     * {@code List<Order>} or {@code Map<String, User>}.
     *
     * <pre>{@code
     * List<Order> orders = serializer.deserialize(bytes, new GenericType<List<Order>>() {});
     * }</pre>
     *
     * @param content     The UTF-8 encoded JSON bytes to deserialize; must not be {@code null}.
     * @param genericType The generic type token of the target type; must not be {@code null}.
     * @param <T>         The target type.
     * @return A new instance of {@code T} populated from the JSON content.
     * @throws IllegalArgumentException if deserialization fails.
     */
    <T> T deserialize(byte[] content, GenericType<T> genericType);
}

