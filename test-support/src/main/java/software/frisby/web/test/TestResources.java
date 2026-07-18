package software.frisby.web.test;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import software.frisby.web.test.resource.EchoResource;
import software.frisby.web.test.resource.HeaderResource;
import software.frisby.web.test.resource.PersonResource;
import software.frisby.web.test.resource.StatusResource;
import software.frisby.web.test.resource.StreamResource;
import software.frisby.web.test.resource.UploadResource;

import java.util.List;

/**
 * Factory for the standard set of JAX-RS resources used in integration tests.
 * <p>
 * Typical usage in a JUnit 5 {@code @BeforeAll}:
 *
 * <pre>{@code
 * @BeforeAll
 * static void startServer() {
 *     server = Server.builder()
 *             .configuration(
 *                     ServerConfiguration.builder()
 *                             .port(8190)
 *                             .host("localhost")
 *                             .serializer(new JacksonSerializer())
 *                             .build()
 *             )
 *             .resources(TestResources.all())
 *             .components(new MultiPartFeature())
 *             .build();
 *     server.start();
 * }
 * }</pre>
 *
 * <p>Note: {@link MultiPartFeature} must be registered as a component separately (as shown above)
 * to enable multipart request handling in {@link UploadResource}.
 */
public final class TestResources {
    private TestResources() {
    }

    /**
     * Returns instances of all standard test resources.
     * <p>
     * Resources included:
     * <ul>
     *   <li>{@link PersonResource} — {@code /persons} — standard CRUD scenarios</li>
     *   <li>{@link EchoResource} — {@code /echo} — round-trip serialization</li>
     *   <li>{@link HeaderResource} — {@code /headers} — header-forwarding verification</li>
     *   <li>{@link StatusResource} — {@code /status/{code}} — error-mapping scenarios</li>
     *   <li>{@link StreamResource} — {@code /stream} — streaming download</li>
     *   <li>{@link UploadResource} — {@code /upload} — multipart file upload</li>
     * </ul>
     *
     * @return An immutable list of resource instances; never {@code null}.
     */
    public static List<Object> all() {
        return List.of(
                new PersonResource(),
                new EchoResource(),
                new HeaderResource(),
                new StatusResource(),
                new StreamResource(),
                new UploadResource()
        );
    }
}

