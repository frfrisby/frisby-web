package software.frisby.web.server;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMessageBodyProviderTest {
    private static final TestJsonSerializer SERIALIZER = new TestJsonSerializer();
    private static final JsonMessageBodyProvider PROVIDER = new JsonMessageBodyProvider(SERIALIZER);
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];

    /**
     * Used purely to capture {@code List<String>} as a {@link java.lang.reflect.ParameterizedType}
     * at runtime without a third-party type-token library.  The field is never read.
     */
    @SuppressWarnings("unused")
    private List<String> listOfStringToken;

    /** The {@code ParameterizedType} for {@code List<String>} — captured from the field above. */
    private static final Type LIST_OF_STRING_TYPE;

    static {
        try {
            LIST_OF_STRING_TYPE = JsonMessageBodyProviderTest.class
                    .getDeclaredField("listOfStringToken")
                    .getGenericType();
        } catch (NoSuchFieldException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    // -------------------------------------------------------------------------
    // readFrom — Class path (type.equals(genericType))
    // -------------------------------------------------------------------------

    @Test
    void readFrom_typeEqualsGenericType_usesClassPath() throws Exception {
        String json = "{\"key\":\"value\"}";

        @SuppressWarnings("unchecked")
        Object result = PROVIDER.readFrom(
                (Class<Object>) (Class<?>) Map.class,
                Map.class,   // genericType == type → Class path taken
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream(json.getBytes())
        );

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) result;
        assertEquals("value", map.get("key"));
    }

    @Test
    void readFrom_concretePojo_typeEqualsGenericType_usesClassPath() throws Exception {
        // PingRequest is a plain record — no type parameters.
        // Jersey passes the same Class object for both type and genericType, so
        // type.equals(genericType) is true and serializer.deserialize(body, type) is called.
        String json = "{\"message\":\"hello\"}";

        @SuppressWarnings("unchecked")
        Object result = PROVIDER.readFrom(
                (Class<Object>) (Class<?>) PingResource.PingRequest.class,
                PingResource.PingRequest.class,  // genericType == type for a non-generic POJO
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream(json.getBytes())
        );

        assertNotNull(result);
        assertEquals(
                new PingResource.PingRequest("hello"),
                result
        );
    }

    // -------------------------------------------------------------------------
    // readFrom — Generic path (genericType != type, i.e. parameterized type)
    // -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void readFrom_parameterizedGenericType_usesGenericPath() throws Exception {
        // LIST_OF_STRING_TYPE is ParameterizedType{List, String} which is != List.class.
        // The condition (null != genericType && !type.equals(genericType)) is true, so
        // serializer.deserialize(body, new GenericType<Object>(genericType) {}) is called.
        String json = "[\"hello\", \"world\"]";

        Object result = PROVIDER.readFrom(
                (Class<Object>) (Class<?>) List.class,
                LIST_OF_STRING_TYPE,   // ParameterizedType{List<String>} ≠ List.class
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream(json.getBytes())
        );

        assertNotNull(result);
        List<String> list = (List<String>) result;
        assertEquals(2, list.size());
        assertTrue(list.contains("hello"));
        assertTrue(list.contains("world"));
    }

    // -------------------------------------------------------------------------
    // writeTo
    // -------------------------------------------------------------------------

    @Test
    void writeTo_serializesEntityToJson() throws Exception {
        Map<String, String> entity = Map.of("hello", "world");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PROVIDER.writeTo(
                entity,
                Map.class,
                Map.class,
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                out
        );

        String json = out.toString();

        assertNotNull(json);
        assertEquals(true, json.contains("hello"));
        assertEquals(true, json.contains("world"));
    }

    @Test
    void writeTo_concretePojo_serializesEntityToJson() throws Exception {
        PingResource.PingRequest entity = new PingResource.PingRequest("hello");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        PROVIDER.writeTo(
                entity,
                PingResource.PingRequest.class,
                PingResource.PingRequest.class,
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                out
        );

        String json = out.toString();

        assertNotNull(json);
        assertTrue(json.contains("hello"), "Serialized JSON must contain the message field value");
    }
}
