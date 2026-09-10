package software.frisby.web.client.sse;

import software.frisby.core.validation.Numbers;

/**
 * A point-in-time occupancy snapshot for a single dispatch pipeline, returned by
 * {@link SseListener#pipelineStats()}.
 * <p>
 * {@link #capacity()} is always {@code handler.capacity() × handler.concurrency()} — the
 * true total ceiling across every concurrent worker arm, not the per-arm
 * {@code capacity()} value configured on {@link SseHandler}/{@link SseBatchHandler}.
 * {@link #inFlight()} can never exceed this value: each arm's own buffer independently
 * enforces its own {@code capacity()} ceiling, and — for a {@code concurrency > 1}
 * handler — {@code frisby-core}'s {@code Router} sums correctly across every arm, so the
 * two numbers are always consistent with each other.
 *
 * @param capacity    The total capacity across every concurrent worker arm
 *                    ({@code capacity × concurrency}).
 * @param concurrency The number of concurrent worker arms this capacity is spread across —
 *                    included so a caller can see how {@link #capacity()} was derived.
 * @param inFlight    The number of items currently queued or being processed anywhere in
 *                    this pipeline, summed across every worker arm.
 * @see SseListener#pipelineStats()
 * @see SsePipelineSnapshot
 */
public record SsePipelineStats(int capacity, int concurrency, int inFlight) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints.
     *
     * @param capacity    the total capacity across every worker arm; must be positive
     * @param concurrency the number of concurrent worker arms; must be positive
     * @param inFlight    the number of items currently in flight; must not be negative
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code capacity} or
     *                                                                           {@code concurrency} is not
     *                                                                           positive, or if {@code inFlight}
     *                                                                           is negative.
     */
    public SsePipelineStats {
        Numbers.positive("capacity", capacity);
        Numbers.positive("concurrency", concurrency);
        Numbers.notNegative("inFlight", inFlight);
    }
}

