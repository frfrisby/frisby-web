package software.frisby.web.client.sse;

import software.frisby.core.validation.Maps;
import software.frisby.core.validation.Values;

import java.util.Map;

/**
 * The full on-demand occupancy snapshot returned by {@link SseListener#pipelineStats()} —
 * one {@link SsePipelineStats} per named handler, plus one for the catch-all
 * unhandled-event pipeline.
 * <p>
 * The unhandled pipeline is deliberately a dedicated field rather than a sentinel entry in
 * {@link #handlers()} — it has no {@code String} event-type key of its own, and inventing
 * one (a reserved constant, or a {@code null} key) would be less honest than simply
 * mirroring {@code DefaultSseListener}'s own internal structure, which already tracks the
 * unhandled pipeline separately from the named-handler map.
 *
 * @param handlers  Stats for every named {@code onEvent} handler, keyed by the same event
 *                  type string passed to {@code onEvent(String, ...)}.
 * @param unhandled Stats for the catch-all unhandled-event pipeline — always present, even
 *                  when no {@code onUnhandledEvent} handler was explicitly registered, since
 *                  {@code DefaultSseListener} always builds one (a no-op fallback in that case).
 * @see SsePipelineStats
 * @see SseListener#pipelineStats()
 */
public record SsePipelineSnapshot(Map<String, SsePipelineStats> handlers, SsePipelineStats unhandled) {
    /**
     * Compact constructor — validates that all fields satisfy their documented constraints,
     * and defensively copies {@code handlers} into an immutable map.
     *
     * @param handlers  stats for every named handler; must not be {@code null}
     * @param unhandled stats for the catch-all pipeline; must not be {@code null}
     * @throws software.frisby.core.validation.NullValueException if {@code handlers} or
     *                                                            {@code unhandled} is {@code null}.
     * @throws NullPointerException                               if {@code handlers} contains a {@code null}
     *                                                            key or value.
     */
    public SsePipelineSnapshot {
        Maps.notNull("handlers", handlers);
        Values.notNull("unhandled", unhandled);

        handlers = Map.copyOf(handlers);
    }
}


