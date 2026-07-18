package software.frisby.web.server;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import software.frisby.web.serial.GenericType;
import software.frisby.web.serial.JsonSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * A JAX-RS {@link MessageBodyReader} and {@link MessageBodyWriter} that bridges
 * Jersey's serialization pipeline to the library's {@link JsonSerializer} abstraction.
 * <p>
 * Registered with the {@link org.glassfish.jersey.server.ResourceConfig} by {@link DefaultServer}
 * so that resource method parameters are deserialized and return values are serialized via
 * the {@link JsonSerializer} supplied to {@link ServerConfigurationBuilder#serializer(JsonSerializer)}.
 * <p>
 * Handles the {@code application/json} media type for all Java types.
 */
@Provider
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
final class JsonMessageBodyProvider implements MessageBodyReader<Object>, MessageBodyWriter<Object> {
    private final JsonSerializer serializer;

    JsonMessageBodyProvider(JsonSerializer serializer) {
        this.serializer = serializer;
    }

    // -------------------------------------------------------------------------
    // MessageBodyReader
    // -------------------------------------------------------------------------

    @Override
    public boolean isReadable(Class<?> type,
                              Type genericType,
                              Annotation[] annotations,
                              MediaType mediaType) {
        return MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType);
    }

    @Override
    public Object readFrom(Class<Object> type,
                           Type genericType,
                           Annotation[] annotations,
                           MediaType mediaType,
                           MultivaluedMap<String, String> httpHeaders,
                           InputStream entityStream) throws IOException, WebApplicationException {
        byte[] body = entityStream.readAllBytes();

        if (!type.equals(genericType)) {
            return serializer.deserialize(body, new GenericType<>(genericType) {
            });
        }

        return serializer.deserialize(body, type);
    }

    // -------------------------------------------------------------------------
    // MessageBodyWriter
    // -------------------------------------------------------------------------

    @Override
    public boolean isWriteable(Class<?> type,
                               Type genericType,
                               Annotation[] annotations,
                               MediaType mediaType) {
        return MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType);
    }

    @Override
    public void writeTo(Object entity,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        entityStream.write(serializer.serialize(entity));
    }
}

