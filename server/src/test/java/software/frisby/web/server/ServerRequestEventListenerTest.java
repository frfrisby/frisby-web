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

        @Test
        void deepCauseChain_appendsAllLevels() {
            StringBuilder sb = new StringBuilder();
            RuntimeException root = new RuntimeException(
                    "level 1",
                    new IOException(
                            "level 2",
                            new IllegalStateException("level 3")
                    )
            );

            ServerRequestEventListener.appendExceptionSection(sb, root);

            String result = sb.toString();
            assertTrue(result.contains("Exception: RuntimeException: level 1"));
            assertTrue(result.contains("Caused by: IOException: level 2"));
            assertTrue(result.contains("Caused by: IllegalStateException: level 3"));
        }

        @Test
        void causeChainExceedingDepthLimit_isTruncated() {
            // Build a chain 15 levels deep — exceeds MAX_CAUSE_DEPTH of 10.
            Throwable deepest = new RuntimeException("leaf");
            for (int i = 0; i < 14; i++) {
                deepest = new RuntimeException("level " + i, deepest);
            }

            StringBuilder sb = new StringBuilder();
            ServerRequestEventListener.appendExceptionSection(sb, deepest);

            String result = sb.toString();
            // 10 "Caused by:" lines at most
            long causedByCount = result.lines()
                    .filter(line -> line.contains("Caused by:"))
                    .count();

            assertEquals(10, causedByCount);
        }
    }
}
