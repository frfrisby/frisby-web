package software.frisby.web.client.sse;

import software.frisby.web.client.Client;
import software.frisby.web.client.PathParameter;
import software.frisby.web.client.RetryDelay;
import software.frisby.web.client.security.SecurityProvider;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * A fluent builder for configuring and opening a typed-callback {@link SseListener}.
 * <p>
 * Obtained from {@link SseListener#builder()}. Navigation methods (path,
 * parameter, header, cookie, security) are re-declared here rather than delegating to
 * a single {@code SseSpec} instance. This is because the builder captures navigation as
 * an immutable template that is replayed against a fresh {@code client.sse()} call on
 * every connection attempt — including reconnects, where the {@code Last-Event-ID}
 * header must reflect the most recently processed event.
 * <p>
 * {@link #client(Client)} is required — {@link #build()} throws if it was never called.
 * It may be called at any point in the fluent chain, not necessarily first.
 * <p>
 * A connection reconnects unconditionally and indefinitely on any connect or
 * reconnect failure — there is no configurable retry limit and no way to disable
 * reconnection. The only way a connection ever stops is an explicit
 * {@link SseListener#close()} call, whether invoked directly by application code or
 * from within an {@link #onError} handler once the caller decides a failure is
 * unrecoverable. See {@link #onError} for details.
 * <p>
 * {@link #build()} is the terminal method — it assembles an {@link SseListener} but
 * opens no connection itself. Call {@link SseListener#connectAsync()} to open one.
 *
 * @see SseListener
 * @see SseMessage
 */
public interface SseListenerBuilder {
    /**
     * Sets the client used to issue the initial connection request and every reconnect
     * attempt.
     * <p>
     * Required — {@link #build()} throws if this method is never called.
     *
     * @param client The client to use.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code client} is null.
     */
    SseListenerBuilder client(Client client);

    /**
     * Sets the URI path for this connection's requests.
     * <p>
     * The path is resolved against the base URI configured on the client. Leading and
     * trailing slashes are normalized automatically.
     *
     * @param path The URI path relative to the client's base URI.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code path} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code path} is blank.
     */
    SseListenerBuilder path(String path);

    /**
     * Sets the URI path for this connection's requests, substituting a single named
     * placeholder.
     * <p>
     * The placeholder must appear in the path surrounded by braces (e.g. {@code {id}}).
     *
     * @param path           The URI path template containing the placeholder.
     * @param parameterId    The name of the placeholder to replace, without braces.
     * @param parameterValue The value to substitute for the placeholder.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code path}, {@code parameterId}, or
     *                                                             {@code parameterValue} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code path}, {@code parameterId}, or
     *                                                             {@code parameterValue} is blank.
     */
    SseListenerBuilder path(String path, String parameterId, String parameterValue);

    /**
     * Sets the URI path for this connection's requests, substituting one or more named
     * placeholders.
     *
     * @param path       The URI path template containing the placeholders.
     * @param parameters The parameters whose names and values will be substituted.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code path} or {@code parameters} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code path} is blank.
     */
    SseListenerBuilder path(String path, PathParameter... parameters);

    /**
     * Adds a query parameter to the connection's request URI.
     *
     * @param name  The query parameter name.
     * @param value The query parameter value.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     */
    SseListenerBuilder parameter(String name, String value);

    /**
     * Adds a multivalued query parameter to the connection's request URI. One
     * {@code name=value} pair is appended for each provided value.
     *
     * @param name   The query parameter name.
     * @param values The query parameter values; one entry is added per value.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code name} is null or {@code values} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code values} is empty.
     * @throws software.frisby.core.validation.BlankValueException      if {@code name} is blank.
     */
    SseListenerBuilder parameter(String name, String... values);

    /**
     * Adds a request header, applied to every connection and reconnection attempt.
     * <p>
     * Use {@link #lastEventId(String)} rather than this method to set an initial
     * {@code Last-Event-ID} — the connection layer manages that header on reconnects
     * once a connection is established.
     *
     * @param name  The header name.
     * @param value The header value.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code name} or {@code value} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code name} or {@code value} is blank.
     * @throws IllegalArgumentException                            if {@code name} is a client-managed header.
     */
    SseListenerBuilder header(String name, String value);

    /**
     * Adds a multivalued request header, applied to every connection and reconnection
     * attempt. One header entry is added per value.
     *
     * @param name   The header name.
     * @param values The header values; one entry is added per value.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException       if {@code name} is null or {@code values} is null.
     * @throws software.frisby.core.validation.MissingElementsException if {@code values} is empty.
     * @throws software.frisby.core.validation.BlankValueException      if {@code name} is blank.
     * @throws IllegalArgumentException                                 if {@code name} is a client-managed header.
     */
    SseListenerBuilder header(String name, String... values);

    /**
     * Adds a cookie to the connection's requests.
     *
     * @param cookie The cookie to include.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code cookie} is null.
     */
    SseListenerBuilder cookie(HttpCookie cookie);

    /**
     * Sets the security provider that will add authentication credentials to the
     * connection's requests, including every reconnect attempt.
     * <p>
     * Providers that refresh credentials (e.g. OAuth2 client-credentials) are
     * re-invoked on every reconnect, exactly as they would be for any other request
     * made through this provider.
     *
     * @param provider The security provider to apply.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code provider} is null.
     */
    SseListenerBuilder security(SecurityProvider provider);

    /**
     * Sets the initial {@code Last-Event-ID} to send on the first connection attempt.
     * <p>
     * Used to resume a stream after a process restart, when no in-memory record of the
     * last received event id is available. Once connected, the connection tracks the
     * most recently processed event's {@code id} itself and applies it automatically to
     * subsequent reconnect attempts.
     *
     * @param id The last successfully processed event id from a previous connection.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code id} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code id} is blank.
     */
    SseListenerBuilder lastEventId(String id);

    /**
     * Sets the policy applied when a handler's dispatch buffer is full and the reader
     * thread has a new event ready to enqueue.
     * <p>
     * Optional; defaults to {@link BufferFullPolicy#BLOCK}.
     *
     * @param policy The backpressure policy to apply.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code policy} is null.
     */
    SseListenerBuilder onBufferFull(BufferFullPolicy policy);

    /**
     * Registers a handler invoked once for every event discarded under
     * {@link BufferFullPolicy#DROP}, receiving the untouched wire-format
     * {@code SseMessage<String>} that was dropped.
     * <p>
     * This connection always logs a {@code WARNING} when a run of drops begins and
     * another when the buffer recovers (summarizing how many events were dropped and
     * over what duration), regardless of whether this handler is registered. The
     * default, out-of-the-box behavior therefore already surfaces "backpressure is
     * occurring and roughly how much impact it's having" without per-event log volume.
     * This handler
     * exists for callers who want per-item granularity instead — e.g. incrementing a
     * metrics counter once per drop, or implementing their own custom
     * summarization/sampling strategy — and fires for every dropped event, unsummarized,
     * regardless of the built-in logging above.
     * <p>
     * Only relevant when {@link #onBufferFull} is set to {@link BufferFullPolicy#DROP};
     * never invoked for {@link BufferFullPolicy#BLOCK} or {@link BufferFullPolicy#DISCONNECT}
     * — {@code DISCONNECT} never actually discards an event (it reconnects and relies on
     * {@code Last-Event-ID} replay instead), so there is nothing to report here for it.
     * <p>
     * Optional; if not set, dropped events are only visible via the built-in logging
     * described above.
     *
     * @param handler The callback invoked with each dropped event.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     */
    SseListenerBuilder onDropped(Consumer<SseMessage<String>> handler);

    /**
     * Sets a custom {@link ExecutorService} backing the reader task and every registered
     * handler's dispatch pipeline.
     * <p>
     * Each registered event type gets its own dedicated dispatch pipeline — capacity,
     * ordering, and concurrency are configured per handler (see {@link SseHandler} /
     * {@link SseBatchHandler}), independent of every other event type's handler. This
     * executor is simply the thread pool those per-handler pipelines draw worker
     * threads from, and — unlike a plain {@link java.util.concurrent.Executor} — is also
     * where the single dedicated reader task itself runs, submitted via
     * {@link ExecutorService#submit(Runnable)} so {@link SseListener#close()} can cancel
     * it individually and precisely via the returned {@code Future}, independent of
     * whatever else (dispatch pipelines mid-graceful-drain, or another {@code SseListener}
     * sharing this same executor) is also running on it. This executor does not itself
     * control ordering, capacity, or concurrency.
     * <p>
     * {@code client-sse} never calls {@code shutdown()}/{@code shutdownNow()} on a
     * caller-supplied executor — only {@code submit()}/{@code execute()}. A caller on
     * Java 21+ wanting the reader task and every dispatch pipeline to run on virtual
     * threads can supply {@code Executors.newVirtualThreadPerTaskExecutor()} directly;
     * no other change is required.
     * <p>
     * Optional; defaults to a dedicated {@code NamedExecutorService} per connection,
     * shut down when the connection is {@link SseListener#close() closed}.
     *
     * @param executor The executor to use; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code executor} is null.
     */
    SseListenerBuilder executor(ExecutorService executor);

    /**
     * Registers a handler for events whose {@code event} field matches {@code event}.
     * <p>
     * {@code handler}'s own {@link SseHandler#capacity(int) capacity} and
     * {@link SseHandler#concurrency(int) concurrency} apply only to this handler's own
     * dispatch pipeline — independent of every other registered handler's settings.
     * <strong>{@code concurrency > 1} forfeits in-order delivery for this event
     * type</strong> — events are fanned out round-robin/least-busy across
     * {@code concurrency} independent worker arms, so a later event may be delivered
     * before an earlier one, and {@code handler}'s callback must be thread-safe.
     *
     * @param event   The event type to handle; matched against an event's explicit
     *                {@code event} field. Events with no {@code event} field at all are
     *                never matched here — they are always routed to
     *                {@link #onUnhandledEvent} instead.
     * @param handler The handler configuration — target type (if any) and callback,
     *                built via {@link SseHandler#of(Class, Consumer)},
     *                {@link SseHandler#of(software.frisby.web.serial.GenericType, Consumer)},
     *                or {@link SseHandler#of(Consumer)}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException         if {@code event} or {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException        if {@code event} is blank.
     * @throws software.frisby.core.validation.DuplicateElementsException if {@code event} is already registered.
     */
    SseListenerBuilder onEvent(String event, SseHandler handler);

    /**
     * Registers a batch handler for events whose {@code event} field matches
     * {@code event}. Events are grouped and delivered together once {@code handler}'s
     * own {@link SseBatchHandler#batchSize(int) batchSize} is reached or
     * {@link SseBatchHandler#batchTimeout(Duration) batchTimeout} elapses, whichever
     * comes first — a ceiling, not a target size to wait for; under low event volume,
     * batches will routinely be delivered well below {@code batchSize}, including a
     * "batch" of a single event.
     * <p>
     * An event within the batch whose data fails to deserialize into {@code handler}'s
     * type is omitted from the delivered batch individually — logged and routed to
     * {@link #onError} with that event's own raw context — rather than discarding the
     * batch entirely. This means a delivered batch can be smaller than the number of
     * events actually collected into it, independent of the {@code batchSize}/
     * {@code batchTimeout} ceiling above.
     * <p>
     * {@code handler}'s own {@link SseBatchHandler#capacity(int) capacity} and
     * {@link SseBatchHandler#concurrency(int) concurrency} apply only to this handler's
     * own dispatch pipeline — independent of every other registered handler's settings.
     * <strong>{@code concurrency > 1} forfeits in-order delivery for this event
     * type</strong> — batches are fanned out round-robin/least-busy across
     * {@code concurrency} independent worker arms, so a later batch may be delivered
     * before an earlier one, and {@code handler}'s callback must be thread-safe.
     * <p>
     * Overloads {@link #onEvent(String, SseHandler)} — disambiguated from the
     * single-event registration by {@code handler}'s type ({@link SseBatchHandler}
     * rather than {@link SseHandler}), mirroring {@link #onUnhandledEvent}'s existing
     * {@code Consumer}/{@code SseHandler} overload pair.
     *
     * @param event   The event type to handle; matched against an event's explicit
     *                {@code event} field. Events with no {@code event} field at all are
     *                never matched here — they are always routed to
     *                {@link #onUnhandledEvent} instead.
     * @param handler The handler configuration — target type (if any), callback, batch
     *                size, and batch timeout — built via
     *                {@link SseBatchHandler#of(Class, Consumer)},
     *                {@link SseBatchHandler#of(software.frisby.web.serial.GenericType, Consumer)},
     *                or {@link SseBatchHandler#of(Consumer)}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException         if {@code event} or {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException        if {@code event} is blank.
     * @throws software.frisby.core.validation.DuplicateElementsException if {@code event} is already registered.
     */
    SseListenerBuilder onEvent(String event, SseBatchHandler handler);

    /**
     * Registers a catch-all handler invoked for any event whose {@code event} field
     * (or {@code "message"} default) has no handler registered via {@link #onEvent}.
     * <p>
     * Convenience shorthand for {@code onUnhandledEvent(SseHandler.of(handler))} — still
     * a real async dispatch pipeline, using {@link SseHandler}'s default capacity
     * ({@code 1024}) and concurrency ({@code 1}), not a degraded path.
     *
     * @param handler The callback invoked with the raw, unhandled message.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     */
    SseListenerBuilder onUnhandledEvent(Consumer<SseMessage<String>> handler);

    /**
     * Registers a catch-all handler, with its own capacity/concurrency tuning, invoked
     * for any event whose {@code event} field (or {@code "message"} default) has no
     * handler registered via {@link #onEvent}.
     *
     * @param handler The handler configuration; must be raw — built via
     *                {@link SseHandler#of(Consumer)} — since an unhandled event has no
     *                known type to deserialize into.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     * @throws IllegalArgumentException                           if {@code handler} was built with a
     *                                                            {@code type()} or {@code genericType()}
     *                                                            present.
     */
    SseListenerBuilder onUnhandledEvent(SseHandler handler);

    /**
     * Registers a catch-all batch handler, with its own capacity/concurrency/batch
     * tuning, invoked for any event whose {@code event} field (or {@code "message"}
     * default) has no handler registered via {@link #onEvent}.
     * <p>
     * Mirrors {@link #onEvent(String, SseBatchHandler)}'s batching semantics exactly —
     * batches are delivered once {@code handler}'s own {@link SseBatchHandler#batchSize(int)
     * batchSize} is reached or {@link SseBatchHandler#batchTimeout(Duration) batchTimeout}
     * elapses, whichever comes first, and an individual item's deserialization failure is
     * omitted from the delivered batch rather than discarding the whole batch. Since this
     * is the catch-all path, {@code handler} must be raw — built via
     * {@link SseBatchHandler#of(Consumer)} — there is no known type to deserialize an
     * unhandled event's data into.
     * <p>
     * Overloads {@link #onUnhandledEvent(SseHandler)} / {@link #onUnhandledEvent(Consumer)}
     * — only one catch-all registration is active at a time; the most recent call among
     * all three {@code onUnhandledEvent} overloads wins.
     *
     * @param handler The handler configuration; must be raw — built via
     *                {@link SseBatchHandler#of(Consumer)} — since an unhandled event has
     *                no known type to deserialize into.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     * @throws IllegalArgumentException                           if {@code handler} was built with a
     *                                                            {@code type()} or {@code genericType()}
     *                                                            present.
     */
    SseListenerBuilder onUnhandledEvent(SseBatchHandler handler);

    /**
     * Registers a handler invoked when a callback exception, deserialization failure,
     * or connect/reconnect failure occurs.
     * <p>
     * {@code handler} receives an {@link SseErrorEvent} pairing the failure with
     * whatever raw event context was available. {@link SseErrorEvent#message()} is
     * present for a deserialization failure or a handler callback exception — always
     * the untouched wire-format {@code data} string, never a typed payload, since that
     * is the one representation guaranteed to survive a deserialization failure. It is
     * empty for a connect/reconnect failure or a whole-batch {@code onEvent(String,
     * SseBatchHandler)} callback exception, neither of which is attributable to a
     * single event.
     * <p>
     * Does not stop the pipeline or close the connection — this connection retries
     * every connect/reconnect failure unconditionally and indefinitely (subject to
     * {@link #reconnectDelay}'s backoff), including failures that can never succeed on
     * their own (e.g. a {@code 404}, a {@code 401}/{@code 403}, or an unresolvable
     * host). There is no built-in retry limit or give-up policy — {@code handler} is
     * the <strong>only</strong> mechanism for terminating a broken connection.
     * Every invocation is also logged at {@code Error} level regardless of whether a
     * handler is registered.
     * <p>
     * A caller that wants to give up after some condition (e.g. a fixed number of
     * consecutive failures, or a specific unrecoverable status code) should track that
     * state itself within {@code handler} and call {@link SseListener#close()} on the
     * listener once that condition is met. Omitting this handler entirely for a
     * connection prone to permanent failure will result in a silent, indefinitely
     * reconnecting connection.
     *
     * @param handler The callback invoked with the failure and its available context.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     */
    SseListenerBuilder onError(Consumer<SseErrorEvent> handler);

    /**
     * Sets the strategy used to compute the delay before each reconnect attempt.
     * <p>
     * When the server supplies a {@code retry} field on a received event, that value
     * takes precedence over this strategy for the very next reconnect attempt only.
     * Subsequent, repeated failures fall back to {@code strategy} — reusing
     * {@link RetryDelay}, the same delay abstraction used by {@code Client}'s
     * {@code RetryPolicy}, gives callers the built-in {@link RetryDelay#fixed(Duration)},
     * {@link RetryDelay#linear(Duration)}, and {@link RetryDelay#exponential(Duration)}
     * strategies (or a custom lambda) for free.
     * <p>
     * Optional; defaults to the server's {@code retry} value when present, otherwise
     * {@link RetryDelay#exponential(Duration) RetryDelay.exponential(Duration.ofSeconds(3))}.
     *
     * @param strategy The reconnect delay strategy.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code strategy} is null.
     */
    SseListenerBuilder reconnectDelay(RetryDelay strategy);

    /**
     * Sets how long {@link SseListener#close()} waits for every handler's dispatch
     * pipeline to finish draining before giving up and returning anyway.
     * <p>
     * Exists specifically to bound {@code close()}'s wait against a scenario the
     * pipeline's own completion signal cannot distinguish on its own: if a caller
     * supplies their own {@link ExecutorService} via {@link #executor} and shuts it
     * down independently, without ever calling {@code close()} first, no worker thread
     * remains to ever resolve that pipeline's completion — an unbounded wait would
     * therefore hang {@code close()} forever. A timeout in that situation is logged at
     * {@code WARNING} rather than thrown, since {@code close()} declares no checked
     * exception.
     * <p>
     * Optional; defaults to 30 seconds.
     *
     * @param timeout The maximum time to wait for dispatch pipelines to drain.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException             if {@code timeout} is null.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code timeout} is not positive.
     */
    SseListenerBuilder closeTimeout(Duration timeout);

    /**
     * Validates the configured navigation, handlers, and options, and assembles an
     * {@link SseListener}. Performs no I/O and starts no threads — call
     * {@link SseListener#connectAsync()} to actually open a connection.
     * <p>
     * At least one handler must be registered — zero or more named {@link #onEvent}
     * registrations, plus, optionally, one catch-all {@link #onUnhandledEvent}
     * registration — before calling this method; a listener with nowhere at all to
     * route received events is never a valid configuration.
     *
     * @return An {@link SseListener} configured from this builder.
     * @throws IllegalStateException if no {@link #onEvent} or {@link #onUnhandledEvent}
     *                               handler of any kind was ever registered.
     */
    SseListener build();
}




