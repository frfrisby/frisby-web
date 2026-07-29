package software.frisby.web.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.frisby.core.validation.NullValueException;

import java.net.URI;
import java.time.Duration;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientBuilderTest {
    private static final URI BASE_URI = URI.create("https://api.example.com");
    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration READ = Duration.ofSeconds(30);

    private static final String NULL_CONFIGURER_MSG = "The 'configurer' value is invalid. The value must not be null.";

    // -------------------------------------------------------------------------
    // configuration(UnaryOperator<ClientConfigurationBuilder>)
    // -------------------------------------------------------------------------

    @Nested
    class ConfigurationLambda {
        @Test
        void lambdaConfigurer_buildsClientWithCorrectUri() {
            Client client = Client.builder()
                    .configuration(c -> c
                            .uri(BASE_URI)
                            .connectTimeout(CONNECT)
                            .readTimeout(READ)
                            .serializer(new TestJsonSerializer()))
                    .build();

            assertEquals(BASE_URI, client.configuration().uri());
        }

        @Test
        void lambdaConfigurer_buildsClientWithCorrectTimeouts() {
            Client client = Client.builder()
                    .configuration(c -> c
                            .uri(BASE_URI)
                            .connectTimeout(CONNECT)
                            .readTimeout(READ)
                            .serializer(new TestJsonSerializer()))
                    .build();

            assertEquals(CONNECT, client.configuration().connectTimeout());
            assertEquals(READ, client.configuration().readTimeout());
        }

        @Test
        void nullConfigurer_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> Client.builder().configuration((java.util.function.UnaryOperator<ClientConfigurationBuilder>) null)
            );

            assertEquals(NULL_CONFIGURER_MSG, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // retryPolicy(UnaryOperator<RetryPolicyBuilder>)
    // -------------------------------------------------------------------------

    @Nested
    class RetryPolicyLambda {
        @Test
        void lambdaConfigurer_buildsClientWithRetryPolicy() {
            Client client = Client.builder()
                    .configuration(c -> c
                            .uri(BASE_URI)
                            .connectTimeout(CONNECT)
                            .readTimeout(READ)
                            .serializer(new TestJsonSerializer()))
                    .retryPolicy(p -> p
                            .maxAttempts(3)
                            .on(RetryPolicy.GATEWAY_ERRORS))
                    .build();

            // If the lambda overload wired through correctly the client builds without error.
            assertEquals(BASE_URI, client.configuration().uri());
        }

        @Test
        void nullConfigurer_throwsNullValueException() {
            NullValueException ex = assertThrows(
                    NullValueException.class,
                    () -> Client.builder().retryPolicy((UnaryOperator<RetryPolicyBuilder>) null)
            );

            assertEquals(NULL_CONFIGURER_MSG, ex.getMessage());
        }
    }
}
