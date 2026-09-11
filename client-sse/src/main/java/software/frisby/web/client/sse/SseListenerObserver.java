package software.frisby.web.client.sse;

/**
 * An observer for an {@link SseListener}'s failures, backpressure, and dispatch-rate
 * telemetry — a single, optional registration point covering every observability concern
 * this module reports.
 * <p>
 * Register an implementation via {@link SseListenerBuilder#observer(SseListenerObserver)}.
 * Every method is a {@code default} no-op — implement only the ones you care about:
 *
 * <pre>{@code
 * SseListener.builder().client(client)
 *         .observer(new SseListenerObserver() {
 *             @Override
 *             public void onEventProcessed(SseEventProcessed event) {
 *                 latencyTimer.record(event.totalLatency());
 *             }
 *         })
 *         // ...
 *         .build();
 * }</pre>
 * <p>
 * Unlike {@link software.frisby.web.client.event.ClientEventListener}'s two mandatory,
 * mutually-exclusive methods, these five methods are not mutually exclusive with each
 * other — {@link #onEventReceived} always fires before {@link #onEventProcessed} for an
 * event that is actually dispatched, and {@link #onReconnect} fires alongside
 * {@link #onError} for the same underlying transport failure. Defaulting every method to a
 * no-op lets a caller implement, say, only {@link #onEventProcessed} for latency metrics
 * without being forced to also acknowledge failure/backpressure handling it doesn't care
 * about.
 * <p>
 * Deliberately not a {@link FunctionalInterface} — with every method defaulted there is no
 * single abstract method for a lambda to implement. Register an anonymous class or a named
 * implementation instead, as shown above.
 * <p>
 * Every method here is invoked from whichever internal thread produced the event
 * (typically the reader thread for {@link #onError}/{@link #onReconnect}/{@link #onDropped}/
 * {@link #onEventReceived}, or a handler's own dispatch-pipeline worker thread for
 * {@link #onEventProcessed}) and is isolated from any exception it throws — a misbehaving
 * observer is logged at {@code WARNING} and cannot take down a reader or worker thread.
 *
 * @see SseListenerBuilder#observer(SseListenerObserver)
 */
public interface SseListenerObserver {
    /**
     * Called when a callback exception, deserialization failure, or connect/reconnect
     * failure occurs.
     * <p>
     * See the class-level javadoc above for the full behavior this mirrors: {@code event}
     * pairs the failure with whatever raw event context was available, does not stop the
     * connection, and is the only mechanism for a caller to decide a connection is
     * unrecoverable and call {@link SseListener#close()}.
     *
     * @param event The failure and its available context.
     */
    default void onError(SseErrorEvent event) {
    }

    /**
     * Called once for every event discarded under {@link BufferFullPolicy#DROP}.
     * <p>
     * Never invoked for {@link BufferFullPolicy#BLOCK} or {@link BufferFullPolicy#DISCONNECT}
     * — {@code DISCONNECT} never actually discards an event, it reconnects and relies on
     * {@code Last-Event-ID} replay instead (see {@link #onReconnect} for that case).
     *
     * @param event The untouched wire-format event that was dropped.
     */
    default void onDropped(SseMessage<String> event) {
    }

    /**
     * Called immediately before this connection waits out the computed delay for a
     * reconnect attempt that follows a setback — either a genuine transport failure or a
     * policy-driven {@link BufferFullPolicy#DISCONNECT}.
     * <p>
     * Never fired for a clean end-of-stream reconnect, since that is not a setback and
     * does not advance the backoff attempt counter. See {@link SseReconnectEvent} for the
     * full field-level detail.
     *
     * @param event The reconnect's cause, triggering event type (if applicable), attempt
     *              count, and computed delay.
     */
    default void onReconnect(SseReconnectEvent event) {
    }

    /**
     * Called the moment a raw event is accepted into a dispatch pipeline, before
     * deserialization.
     * <p>
     * See {@link SseEventReceived} for the full field-level detail, including how
     * {@link SseEventReceived#registeredAs()} distinguishes a named handler's pipeline from
     * the catch-all unhandled-event pipeline.
     *
     * @param event The received event's wire-level identity.
     */
    default void onEventReceived(SseEventReceived event) {
    }

    /**
     * Called the moment a handler's callback returns or throws for a given event, carrying
     * both the end-to-end latency since the event was received and the callback's own
     * execution time.
     * <p>
     * Fires unconditionally, regardless of outcome — {@link SseEventProcessed#succeeded()}
     * distinguishes a normal return from a thrown exception, so telemetry built purely on
     * this callback never has a blind spot for failed invocations. A thrown callback
     * exception is still separately and fully reported, with its cause and raw context,
     * via {@link #onError}.
     * <p>
     * See {@link SseEventProcessed} for the full field-level detail, including how a batch
     * handler reports one {@code SseEventProcessed} per item in the delivered batch.
     *
     * @param event The processed event's wire-level identity, latency measurements, and outcome.
     */
    default void onEventProcessed(SseEventProcessed event) {
    }
}

