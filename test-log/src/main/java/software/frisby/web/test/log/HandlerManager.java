package software.frisby.web.test.log;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Manages the singleton {@link CapturingHandler} installed on the root JUL logger and routes
 * captured {@link SystemLogEvent} instances to the currently active
 * {@link DefaultSystemLogVerifier}.
 * <p>
 * The handler and root logger level are configured once when this class is first loaded.
 * Only one verifier may be active at a time; registering a new verifier replaces the previous
 * one.  Tests must use try-with-resources on {@link SystemLogVerifier} to guarantee that
 * {@link #reset()} is called after each test.
 */
final class HandlerManager {
    // Never read after initialization.  The field exists solely to trigger install() exactly once
    // via static field initialization when this class is first loaded.  install() registers the
    // CapturingHandler on the root JUL logger as a permanent side effect; the root logger then
    // holds the only strong reference needed to keep the handler alive.
    @SuppressWarnings("unused")
    private static final CapturingHandler HANDLER = install();

    private static final AtomicReference<DefaultSystemLogVerifier> ACTIVE = new AtomicReference<>();

    private HandlerManager() {
    }

    static void register(DefaultSystemLogVerifier verifier) {
        ACTIVE.set(verifier);
    }

    static void reset() {
        ACTIVE.set(null);
    }

    static void accept(SystemLogEvent event) {
        DefaultSystemLogVerifier verifier = ACTIVE.get();

        if (null != verifier) {
            verifier.accept(event);
        }
    }

    private static CapturingHandler install() {
        CapturingHandler handler = new CapturingHandler();

        Logger root = Logger.getLogger("");
        root.setLevel(java.util.logging.Level.ALL);
        root.addHandler(handler);

        return handler;
    }
}

