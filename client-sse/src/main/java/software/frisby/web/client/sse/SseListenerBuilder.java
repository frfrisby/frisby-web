package software.frisby.web.client.sse;

import software.frisby.web.client.Client;
import software.frisby.web.client.PathParameter;
import software.frisby.web.client.RetryDelay;
import software.frisby.web.client.security.SecurityProvider;
import software.frisby.web.serial.GenericType;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A fluent builder for configuring and opening a typed-callback {@link SseListener}.
 * <p>
 * Obtained from {@code SseListener.builder(Client)}. Navigation methods (path,
 * parameter, header, cookie, security) are re-declared here rather than delegating to
 * a single {@code SseSpec} instance, because the builder captures navigation as an
 * immutable template that is replayed against a fresh {@code client.sse()} call on
 * every connection attempt — including reconnects, where the {@code Last-Event-ID}
 * header must reflect the most recently processed event.
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
     * Sets the capacity of the dispatch buffer sitting between the reader thread and
     * the dispatch executor.
     * <p>
     * Optional; defaults to {@code 1024}.
     *
     * @param capacity The buffer capacity; must be positive.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code capacity} is not positive.
     */
    SseListenerBuilder bufferCapacity(int capacity);

    /**
     * Sets the policy applied when the dispatch buffer is full and the reader thread
     * has a new event ready to enqueue.
     * <p>
     * Optional; defaults to {@link BufferFullPolicy#BLOCK}.
     *
     * @param policy The backpressure policy to apply.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code policy} is null.
     */
    SseListenerBuilder onBufferFull(BufferFullPolicy policy);

    /**
     * Sets a custom {@link Executor} backing every registered handler's dispatch
     * pipeline.
     * <p>
     * Each registered event type gets its own dedicated dispatch pipeline — ordering
     * and concurrency are configured per handler (see the {@code concurrency} parameter
     * on {@link #onEvent(String, Class, Consumer, int) onEvent} and
     * {@link #onEventBatch(String, Class, Consumer, int) onEventBatch}), independent of
     * every other event type's handler. This executor is simply the thread pool those
     * per-handler pipelines draw worker threads from; it does not itself control
     * ordering or concurrency.
     * <p>
     * Optional; defaults to a dedicated {@code NamedExecutorService} per connection,
     * shut down when the connection is {@link SseListener#close() closed}.
     *
     * @param executor The executor to use; must not be {@code null}.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code executor} is null.
     */
    SseListenerBuilder executor(Executor executor);

    /**
     * Registers a typed handler for events whose {@code event} field matches
     * {@code event}.
     * <p>
     * The raw wire-format {@code data} string is deserialized via the {@link Client}'s
     * configured {@code JsonSerializer} and delivered as {@link SseMessage#body()}.
     *
     * @param event   The event type to handle; events with no {@code event} field are
     *                matched against {@code "message"}.
     * @param type    The type to deserialize the event data into.
     * @param handler The callback invoked with the message.
     * @param <T>     The deserialized payload type.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code event}, {@code type}, or
     *                                                             {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code event} is blank.
     */
    <T> SseListenerBuilder onEvent(String event, Class<T> type, Consumer<SseMessage<T>> handler);

    /**
     * Registers a typed handler for events whose {@code event} field matches
     * {@code event}, dispatched across {@code concurrency} concurrent worker arms.
     * <p>
     * The raw wire-format {@code data} string is deserialized via the {@link Client}'s
     * configured {@code JsonSerializer} and delivered as {@link SseMessage#body()}.
     * <p>
     * <strong>{@code concurrency > 1} forfeits in-order delivery for this event
     * type</strong> — events are fanned out round-robin/least-busy across
     * {@code concurrency} independent worker arms, so a later event may be delivered
     * before an earlier one. {@code handler} is invoked concurrently from up to
     * {@code concurrency} threads and must be thread-safe. Use this overload only for
     * handlers whose processing latency (I/O, external calls, etc.) would otherwise
     * bottleneck a high-volume event type; {@code concurrency = 1} (the other overload)
     * is equivalent to this one and preserves today's in-order, single-arm delivery.
     *
     * @param event       The event type to handle; events with no {@code event} field
     *                    are matched against {@code "message"}.
     * @param type        The type to deserialize the event data into.
     * @param handler     The callback invoked with the message; must be thread-safe
     *                    when {@code concurrency > 1}.
     * @param concurrency The number of concurrent worker arms dispatching to
     *                    {@code handler}; must be positive.
     * @param <T>         The deserialized payload type.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException               if {@code event}, {@code type}, or
     *                                                                          {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException              if {@code event} is blank.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code concurrency} is not
     *                                                                          positive.
     */
    <T> SseListenerBuilder onEvent(String event, Class<T> type, Consumer<SseMessage<T>> handler, int concurrency);

    /**
     * Registers a typed handler for events whose {@code event} field matches
     * {@code event}, deserializing into a generic type such as {@code List<Item>}.
     *
     * @param event   The event type to handle; events with no {@code event} field are
     *                matched against {@code "message"}.
     * @param type    The generic type to deserialize the event data into.
     * @param handler The callback invoked with the message.
     * @param <T>     The deserialized payload type.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code event}, {@code type}, or
     *                                                             {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code event} is blank.
     */
    <T> SseListenerBuilder onEvent(String event, GenericType<T> type, Consumer<SseMessage<T>> handler);

    /**
     * Registers a typed handler for events whose {@code event} field matches
     * {@code event}, deserializing into a generic type such as {@code List<Item>},
     * dispatched across {@code concurrency} concurrent worker arms.
     * <p>
     * <strong>{@code concurrency > 1} forfeits in-order delivery for this event
     * type</strong> — see {@link #onEvent(String, Class, Consumer, int)} for the full
     * explanation of this trade-off and the thread-safety requirement it places on
     * {@code handler}.
     *
     * @param event       The event type to handle; events with no {@code event} field
     *                    are matched against {@code "message"}.
     * @param type        The generic type to deserialize the event data into.
     * @param handler     The callback invoked with the message; must be thread-safe
     *                    when {@code concurrency > 1}.
     * @param concurrency The number of concurrent worker arms dispatching to
     *                    {@code handler}; must be positive.
     * @param <T>         The deserialized payload type.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException               if {@code event}, {@code type}, or
     *                                                                          {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException              if {@code event} is blank.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code concurrency} is not
     *                                                                          positive.
     */
    <T> SseListenerBuilder onEvent(String event, GenericType<T> type, Consumer<SseMessage<T>> handler, int concurrency);

    /**
     * Registers a raw handler for events whose {@code event} field matches
     * {@code event}, receiving an {@code SseMessage<String>} whose
     * {@link SseMessage#body()} is the untouched wire-format {@code data} string.
     *
     * @param event   The event type to handle; events with no {@code event} field are
     *                matched against {@code "message"}.
     * @param handler The callback invoked with the raw message.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code event} or {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code event} is blank.
     */
    SseListenerBuilder onEvent(String event, Consumer<SseMessage<String>> handler);

    /**
     * Registers a raw handler for events whose {@code event} field matches
     * {@code event}, receiving an {@code SseMessage<String>} whose
     * {@link SseMessage#body()} is the untouched wire-format {@code data} string,
     * dispatched across {@code concurrency} concurrent worker arms.
     * <p>
     * <strong>{@code concurrency > 1} forfeits in-order delivery for this event
     * type</strong> — see {@link #onEvent(String, Class, Consumer, int)} for the full
     * explanation of this trade-off and the thread-safety requirement it places on
     * {@code handler}.
     *
     * @param event       The event type to handle; events with no {@code event} field
     *                    are matched against {@code "message"}.
     * @param handler     The callback invoked with the raw message; must be
     *                    thread-safe when {@code concurrency > 1}.
     * @param concurrency The number of concurrent worker arms dispatching to
     *                    {@code handler}; must be positive.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException               if {@code event} or
     *                                                                          {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException              if {@code event} is blank.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code concurrency} is not
     *                                                                          positive.
     */
    SseListenerBuilder onEvent(String event, Consumer<SseMessage<String>> handler, int concurrency);

    /**
     * Registers a catch-all handler invoked for any event whose {@code event} field
     * (or {@code "message"} default) has no handler registered via {@link #onEvent}
     * or {@link #onEventBatch}.
     *
     * @param handler The callback invoked with the raw, unhandled message.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     */
    SseListenerBuilder onUnhandledEvent(Consumer<SseMessage<String>> handler);

    /**
     * Registers a typed batch handler for events whose {@code event} field matches
     * {@code event}. Events are grouped and delivered together once {@link #batchSize}
     * is reached or {@link #batchTimeout} elapses, whichever comes first.
     *
     * @param event   The event type to handle; events with no {@code event} field are
     *                matched against {@code "message"}.
     * @param type    The type to deserialize each event's data into.
     * @param handler The callback invoked with the batch of messages.
     * @param <T>     The deserialized payload type.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code event}, {@code type}, or
     *                                                             {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code event} is blank.
     */
    <T> SseListenerBuilder onEventBatch(String event, Class<T> type, Consumer<List<SseMessage<T>>> handler);

    /**
     * Registers a typed batch handler for events whose {@code event} field matches
     * {@code event}, dispatched across {@code concurrency} concurrent worker arms.
     * Events are grouped and delivered together once {@link #batchSize} is reached or
     * {@link #batchTimeout} elapses, whichever comes first.
     * <p>
     * <strong>{@code concurrency > 1} forfeits in-order delivery for this event
     * type</strong> — batches are fanned out round-robin/least-busy across
     * {@code concurrency} independent worker arms, so a later batch may be delivered
     * before an earlier one, and each batch is still homogeneous (all items share the
     * same {@code event()} value) but is only ordered within itself. {@code handler} is
     * invoked concurrently from up to {@code concurrency} threads and must be
     * thread-safe.
     *
     * @param event       The event type to handle; events with no {@code event} field
     *                    are matched against {@code "message"}.
     * @param type        The type to deserialize each event's data into.
     * @param handler     The callback invoked with the batch of messages; must be
     *                    thread-safe when {@code concurrency > 1}.
     * @param concurrency The number of concurrent worker arms dispatching to
     *                    {@code handler}; must be positive.
     * @param <T>         The deserialized payload type.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException               if {@code event}, {@code type}, or
     *                                                                          {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException              if {@code event} is blank.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code concurrency} is not
     *                                                                          positive.
     */
    <T> SseListenerBuilder onEventBatch(String event, Class<T> type, Consumer<List<SseMessage<T>>> handler,
                                         int concurrency);

    /**
     * Registers a typed batch handler for events whose {@code event} field matches
     * {@code event}, deserializing each event's data into a generic type such as
     * {@code List<Item>}. Events are grouped and delivered together once
     * {@link #batchSize} is reached or {@link #batchTimeout} elapses, whichever comes
     * first.
     *
     * @param event   The event type to handle; events with no {@code event} field are
     *                matched against {@code "message"}.
     * @param type    The generic type to deserialize each event's data into.
     * @param handler The callback invoked with the batch of messages.
     * @param <T>     The deserialized payload type.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code event}, {@code type}, or
     *                                                             {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code event} is blank.
     */
    <T> SseListenerBuilder onEventBatch(String event, GenericType<T> type, Consumer<List<SseMessage<T>>> handler);

    /**
     * Registers a typed batch handler for events whose {@code event} field matches
     * {@code event}, deserializing each event's data into a generic type such as
     * {@code List<Item>}, dispatched across {@code concurrency} concurrent worker arms.
     * Events are grouped and delivered together once {@link #batchSize} is reached or
     * {@link #batchTimeout} elapses, whichever comes first.
     * <p>
     * <strong>{@code concurrency > 1} forfeits in-order delivery for this event
     * type</strong> — see
     * {@link #onEventBatch(String, Class, Consumer, int)} for the full explanation of
     * this trade-off and the thread-safety requirement it places on {@code handler}.
     *
     * @param event       The event type to handle; events with no {@code event} field
     *                    are matched against {@code "message"}.
     * @param type        The generic type to deserialize each event's data into.
     * @param handler     The callback invoked with the batch of messages; must be
     *                    thread-safe when {@code concurrency > 1}.
     * @param concurrency The number of concurrent worker arms dispatching to
     *                    {@code handler}; must be positive.
     * @param <T>         The deserialized payload type.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException               if {@code event}, {@code type}, or
     *                                                                          {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException              if {@code event} is blank.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code concurrency} is not
     *                                                                          positive.
     */
    <T> SseListenerBuilder onEventBatch(String event, GenericType<T> type, Consumer<List<SseMessage<T>>> handler,
                                         int concurrency);

    /**
     * Registers a raw batch handler for events whose {@code event} field matches
     * {@code event}, receiving each batch as a {@link List} of
     * {@code SseMessage<String>} whose {@link SseMessage#body()} is the untouched
     * wire-format {@code data} string. Events are grouped and delivered together once
     * {@link #batchSize} is reached or {@link #batchTimeout} elapses, whichever comes
     * first.
     *
     * @param event   The event type to handle; events with no {@code event} field are
     *                matched against {@code "message"}.
     * @param handler The callback invoked with the batch of raw messages.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException  if {@code event} or {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException if {@code event} is blank.
     */
    SseListenerBuilder onEventBatch(String event, Consumer<List<SseMessage<String>>> handler);

    /**
     * Registers a raw batch handler for events whose {@code event} field matches
     * {@code event}, receiving each batch as a {@link List} of
     * {@code SseMessage<String>} whose {@link SseMessage#body()} is the untouched
     * wire-format {@code data} string, dispatched across {@code concurrency} concurrent
     * worker arms. Events are grouped and delivered together once {@link #batchSize} is
     * reached or {@link #batchTimeout} elapses, whichever comes first.
     * <p>
     * <strong>{@code concurrency > 1} forfeits in-order delivery for this event
     * type</strong> — see
     * {@link #onEventBatch(String, Class, Consumer, int)} for the full explanation of
     * this trade-off and the thread-safety requirement it places on {@code handler}.
     *
     * @param event       The event type to handle; events with no {@code event} field
     *                    are matched against {@code "message"}.
     * @param handler     The callback invoked with the batch of raw messages; must be
     *                    thread-safe when {@code concurrency > 1}.
     * @param concurrency The number of concurrent worker arms dispatching to
     *                    {@code handler}; must be positive.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException               if {@code event} or
     *                                                                          {@code handler} is null.
     * @throws software.frisby.core.validation.BlankValueException              if {@code event} is blank.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code concurrency} is not
     *                                                                          positive.
     */
    SseListenerBuilder onEventBatch(String event, Consumer<List<SseMessage<String>>> handler, int concurrency);

    /**
     * Sets the maximum number of events collected into a single batch before it is
     * delivered to a registered {@link #onEventBatch} handler.
     * <p>
     * Optional; defaults to {@code 100}.
     *
     * @param maxBatchSize The maximum batch size; must be positive.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NumericValueOutsideRangeException if {@code maxBatchSize} is not
     *                                                                          positive.
     */
    SseListenerBuilder batchSize(int maxBatchSize);

    /**
     * Sets the maximum time a partially filled batch waits before being flushed to a
     * registered {@link #onEventBatch} handler.
     * <p>
     * Optional; defaults to {@code 250ms}.
     *
     * @param flushTimeout The flush timeout; must be positive.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException           if {@code flushTimeout} is null.
     * @throws software.frisby.core.validation.DurationOutsideRangeException if {@code flushTimeout} is not positive.
     */
    SseListenerBuilder batchTimeout(Duration flushTimeout);

    /**
     * Registers a handler invoked when a callback exception, deserialization failure,
     * or connect/reconnect failure occurs.
     * <p>
     * Does not stop the pipeline or close the connection — this connection retries
     * every connect/reconnect failure unconditionally and indefinitely (subject to
     * {@link #reconnectDelay}'s backoff), including failures that can never succeed on
     * their own (e.g. a {@code 404}, a {@code 401}/{@code 403}, or an unresolvable
     * host). There is no built-in retry limit or give-up policy — {@code handler} is
     * the <strong>only</strong> mechanism for ever terminating a broken connection.
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
     * @param handler The callback invoked with the failure.
     * @return This builder instance.
     * @throws software.frisby.core.validation.NullValueException if {@code handler} is null.
     */
    SseListenerBuilder onError(Consumer<Throwable> handler);

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
     * Validates the configured navigation, handlers, and options, and assembles an
     * {@link SseListener}. Performs no I/O and starts no threads — call
     * {@link SseListener#connectAsync()} to actually open a connection.
     *
     * @return An {@link SseListener} configured from this builder.
     */
    SseListener build();
}




