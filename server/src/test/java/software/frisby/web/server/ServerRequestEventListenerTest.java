package software.frisby.web.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRequestEventListenerTest {
    @Nested
    class AppendExceptionSection {
        @Test
        void nullMessageOrCause_returnEmpty() {
            StringBuilder sb = new StringBuilder();
            RuntimeException cause = new RuntimeException();

            ServerRequestEventListener.appendExceptionSection(sb, cause);

            assertTrue(sb.isEmpty());
        }

        @Test
        void nullMessageWithCause_appendsCause() {
            StringBuilder sb = new StringBuilder();
            RuntimeException cause = new RuntimeException((String) null, new IOException("An IO error occurred"));

            ServerRequestEventListener.appendExceptionSection(sb, cause);

            assertEquals(
                    """
                            
                              Exception: RuntimeException
                                Caused by: IOException: An IO error occurred\
                            """,
                    sb.toString()
            );
        }

        @Test
        void blankMessageWithCause_appendCause() {
            StringBuilder sb = new StringBuilder();
            RuntimeException cause = new RuntimeException("", new IOException("An IO error occurred"));

            ServerRequestEventListener.appendExceptionSection(sb, cause);

            assertEquals(
                    """
                            
                              Exception: RuntimeException
                                Caused by: IOException: An IO error occurred\
                            """,
                    sb.toString()
            );
        }

        @Test
        void blankMessageWithCauseAndBlankMessage_appendCause() {
            StringBuilder sb = new StringBuilder();
            RuntimeException cause = new RuntimeException("", new IOException(""));

            ServerRequestEventListener.appendExceptionSection(sb, cause);

            assertEquals(
                    """
                            
                              Exception: RuntimeException
                                Caused by: IOException\
                            """,
                    sb.toString()
            );
        }

        @Test
        void blankMessageWithCauseAndNullMessage_appendCause() {
            StringBuilder sb = new StringBuilder();
            RuntimeException cause = new RuntimeException("", new IOException((String) null));

            ServerRequestEventListener.appendExceptionSection(sb, cause);

            assertEquals(
                    """
                            
                              Exception: RuntimeException
                                Caused by: IOException\
                            """,
                    sb.toString()
            );
        }
    }
}
