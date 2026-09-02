package software.frisby.web.client.sse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link DefaultSseListener#throwIfFatal}.
 * <p>
 * {@code throwIfFatal} is deliberately package-private (not {@code private}) so it can be
 * exercised directly, in isolation, without needing to force an actual
 * {@link VirtualMachineError}/{@link LinkageError} to occur inside a real callback — see the
 * method's own javadoc.
 */
class DefaultSseListenerThrowIfFatalTest {
    @Test
    void outOfMemoryError_rethrown() {
        OutOfMemoryError error = new OutOfMemoryError("boom");

        OutOfMemoryError thrown = assertThrows(
                OutOfMemoryError.class,
                () -> DefaultSseListener.throwIfFatal(error)
        );

        assertSame(error, thrown);
    }

    @Test
    void stackOverflowError_rethrown() {
        StackOverflowError error = new StackOverflowError("boom");

        StackOverflowError thrown = assertThrows(
                StackOverflowError.class,
                () -> DefaultSseListener.throwIfFatal(error)
        );

        assertSame(error, thrown);
    }

    @Test
    void internalError_rethrown() {
        InternalError error = new InternalError("boom");

        InternalError thrown = assertThrows(
                InternalError.class,
                () -> DefaultSseListener.throwIfFatal(error)
        );

        assertSame(error, thrown);
    }

    @Test
    void unknownError_rethrown() {
        UnknownError error = new UnknownError("boom");

        UnknownError thrown = assertThrows(
                UnknownError.class,
                () -> DefaultSseListener.throwIfFatal(error)
        );

        assertSame(error, thrown);
    }

    @Test
    void noClassDefFoundError_rethrown() {
        NoClassDefFoundError error = new NoClassDefFoundError("boom");

        NoClassDefFoundError thrown = assertThrows(
                NoClassDefFoundError.class,
                () -> DefaultSseListener.throwIfFatal(error)
        );

        assertSame(error, thrown);
    }

    @Test
    void runtimeException_returnsNormally() {
        assertDoesNotThrow(() -> DefaultSseListener.throwIfFatal(new RuntimeException("boom")));
    }

    @Test
    void assertionError_returnsNormally() {
        assertDoesNotThrow(() -> DefaultSseListener.throwIfFatal(new AssertionError("boom")));
    }

    @Test
    void plainError_returnsNormally() {
        assertDoesNotThrow(() -> DefaultSseListener.throwIfFatal(new Error("boom")));
    }

    @Test
    void checkedException_returnsNormally() {
        assertDoesNotThrow(() -> DefaultSseListener.throwIfFatal(new Exception("boom")));
    }
}



