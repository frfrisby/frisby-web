# frisby-core Concurrency — AI Context Reference

**Package:** `software.frisby.core.concurrency`
**Fluent builders:** `software.frisby.core.concurrency.fluent`
**Dependency:** `software.frisby.core:concurrency`

This file is designed to be attached to an AI assistant's context window.  It covers
the full concurrency API with exact call signatures, defaults, and representative examples.

---

## Prefer the Fluent API

Always reach for `Pipeline.builder()` or `OpenPipeline.builder()` first.  Manual
`block.linkTo()` wiring is only needed for dynamic topologies, reuse of pre-built blocks,
or custom `Target<T>` / `Source<T>` implementations.  See the [Advanced / Manual Wiring](#advanced--manual-wiring)
section below for those cases.

---

## Pipeline.builder() — Terminal Pipeline

```java
import software.frisby.core.concurrency.NamedExecutorService;
import software.frisby.core.concurrency.fluent.*;

NamedExecutorService executor = NamedExecutorService.builder()
        .threadPrefix("my-pipeline")
        .poolSize(4)          // optional; defaults to available processors
        .build();

// Minimal — single synchronous terminal consumer
Pipeline<Message> p = Pipeline.<Message>builder()
        .from(message -> process(message));

// Typical — async head decouples the posting thread
Pipeline<Order> p = Pipeline.<Order>builder()
        .executor(executor)
        .from(Buffer.of(Order.class))
        .to(order -> store(order));

// Multi-stage
Pipeline<Order> p = Pipeline.<Order>builder()
        .executor(executor)
        .from(Buffer.of(Order.class).capacity(2048))
        .then(Transform.of(Order.class, Invoice.class)
                .transform(order -> invoiceService.create(order)))
        .then(Tap.of(Invoice.class)
                .consumer(invoice -> metrics.increment("invoices")))
        .to(invoice -> store(invoice));

// Post, drain, shut down
p.post(order);
p.complete();
p.awaitCompletion();                          // blocks until fully drained
p.awaitCompletion(Duration.ofSeconds(30));    // with timeout; returns boolean
p.completion().thenRun(() -> log("done"));    // async variant
executor.shutdown();
```

**`from()` overloads:**

| Overload                    | Description                                                          |
|-----------------------------|----------------------------------------------------------------------|
| `.from(PipelineStage<T,N>)` | Head is a stage with input `T` and output `N`; returns `Chain<T,N>`. |
| `.from(OpenPipeline<T,N>)`  | Head is a pre-assembled open pipeline; returns `Chain<T,N>`.         |
| `.from(PipelineTarget<T>)`  | Single-block terminal pipeline (no `then()`).                        |
| `.from(Consumer<T>)`        | Shorthand for a single `Action` terminal.                            |

**`to()` overloads (on `Chain<H,O>`):**

| Overload                 | Description                                                                  |
|--------------------------|------------------------------------------------------------------------------|
| `.to(Consumer<O>)`       | Wraps in an `Action` and closes the chain.                                   |
| `.to(Action<O>)`         | Closes with a configured `Action` stage.                                     |
| `.to(PipelineTarget<O>)` | Closes with any `PipelineTarget` (use `() -> existingBlock` for raw blocks). |
| `.to(Pipeline<O>)`       | Closes by linking to a pre-assembled terminal pipeline.                      |

---

## OpenPipeline.builder() — Open Pipeline

Use when the pipeline tail must feed a shared downstream stage (typically inside an `OpenRouter`).

```java
OpenPipeline<Message, List<Message>> arm = OpenPipeline.builder()
        .executor(executor)
        .from(Buffer.of(Message.class))
        .then(Group.of(Message.class, String.class)
                .groupingFunction(Message::customerId)
                .maxGroupSize(128)
                .timeout(Duration.ofSeconds(10)))
        .build();     // closes chain; returns OpenPipeline<Message, List<Message>>
```

`OpenPipeline<I, O>` extends both `Pipeline<I>` and `Stage<I, O>` — it is a runnable
pipeline *and* a source that can be linked downstream.

---

## Fluent Stage Builders

All builders are in `software.frisby.core.concurrency.fluent`.

### Type-inference overloads

Every builder has three `of()` variants:

```java
Buffer.of()                    // raw — type inferred from context
Buffer.of(Message.class)       // Class<T> — most common; not stored
Buffer.of(new GenericType<List<Message>>(){})  // GenericType<T> — for generic item types
```

`GenericType` is in `software.frisby.core.concurrency` (not the `fluent` sub-package).

`ofLists(T.class)` shorthand is available on all pass-through and terminal stage builders:

```java
Buffer.ofLists(Message.class)      // Buffer<List<Message>>
Delay.ofLists(Message.class)       // Delay<List<Message>>
PriorityBuffer.ofLists(Message.class) // PriorityBuffer<List<Message>>
Tap.ofLists(Message.class)         // Tap<List<Message>>
Action.ofLists(Message.class)      // Action<List<Message>>
Broadcast.ofLists(Message.class)   // Broadcast<List<Message>>
Router.ofLists(Message.class)      // Router<List<Message>>
Branch.ofLists(Message.class)      // Branch<List<Message>>
Transform.ofLists(Message.class)   // Transform<List<Message>, List<Message>>
OpenRouter.ofLists(Message.class)  // OpenRouter<List<Message>, List<Message>>
```

Block interfaces (`BufferBlock`, `BatchBlock`, etc.) likewise have a `builder(Class<T>)`
inference overload that delegates directly to `builder()` — useful in the same contexts
where `of(Class<T>)` is useful for the fluent helpers.

---

### Async Stages

#### `Buffer<T>` — pass-through, `T → T`

```java
Buffer.of(Message.class)
    .capacity(1024)             // optional; default 1024
    .errorOccurredHandler(...)  // optional
    .itemPostedHandler(...)     // optional
    .itemDeliveredHandler(...)  // optional
```

#### `PriorityBuffer<T>` — priority pass-through, `T → T`

```java
PriorityBuffer.of(Task.class)
    .capacity(1024)             // optional; default 1024
    .comparator(Comparator.comparingInt(Task::priority).reversed())  // required
    .errorOccurredHandler(...)
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

#### `Batch<T>` — accumulator, `T → List<T>`

```java
Batch.of(Message.class)
    .batchSize(128)             // optional; default 128
    .timeout(Duration.ofSeconds(5))  // optional; default 5 s — flush even if not full
    .capacity(1024)             // optional; default 1024
    .errorOccurredHandler(...)
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

#### `Group<T, K>` — key-based grouping, `T → List<T>`

```java
Group.of(Message.class, String.class)
    .groupingFunction(Message::customerId)   // required — extracts the group key
    .maxGroupSize(128)          // optional; default 128
    .timeout(Duration.ofSeconds(10))         // optional; default 10 s
    .idleTimeout(Duration.ofSeconds(5))      // optional; default 5 s
    .capacity(1024)             // optional; default 1024
    .groupObserver(observer)    // optional — override flush policy per group
    .errorOccurredHandler(...)
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

A group is flushed when *any* of these conditions is met: `maxGroupSize` reached,
`timeout` elapsed, or `idleTimeout` elapsed since the last item in that group.

#### `Delay<T>` — hold-and-release, `T → T`

```java
// Fixed delay for all items
Delay.of(Task.class)
    .delay(Duration.ofSeconds(5))   // required — use this or the Function overload
    .capacity(1024)

// Per-item delay
Delay.of(Task.class)
    .delay(task -> task.scheduledDelay())
    .capacity(1024)
```

**Drain behavior:** `complete()` flushes all held items immediately; delays are not honored on drain.

---

### Synchronous Intermediate Stages

#### `Tap<T>` — side effect pass-through, `T → T`

```java
Tap.of(Order.class)
    .consumer(order -> auditLog.record(order))   // required
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

The original item is forwarded downstream unchanged after the consumer returns.
If the consumer throws, the exception propagates to the posting thread and the
item is **not** forwarded.

#### `Transform<T, R>` — type-changing transform, `T → R`

```java
Transform.of(Order.class, Invoice.class)
    .transform(order -> invoiceService.create(order))   // required; null result → item dropped
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

#### `Expand<T>` — list splitter, `List<T> → T`

```java
Expand.of(Message.class)    // no configuration — expands List<Message> into individual Messages
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

---

### Terminal / Routing Stages

Terminal stages are passed to `.to()` on a `Chain`, or directly to
`Pipeline.builder().from()` for single-stage pipelines.

#### `Action<T>` — terminal consumer

```java
Action.of(Invoice.class)
    .action(invoice -> store(invoice))   // required
    .itemPostedHandler(...)
```

Shorthand — pass a `Consumer<T>` directly to `.to()`:

```java
.to(invoice -> store(invoice))
```

#### `Branch<T>` — conditional routing to separate pipelines

```java
Branch.of(Order.class)
    .when(order -> order.isPriority(), priorityPipeline)     // first matching predicate wins
    .when(order -> order.isExpress(), expressPipeline)
    .otherwise(standardPipeline)                             // required
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

Each arm is a fully assembled `Pipeline<T>`.  `otherwise()` is required; building
without it throws `IllegalArgumentException`.

#### `Broadcast<T>` — fan-out to all arms

```java
Broadcast.of(Message.class)
    .targets(List.of(analyticsP, archiveP, alertP))   // required; >= 1 pipeline
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

#### `Router<T>` — fan-out to one of N terminal arms

```java
Router.of(Message.class)
    .sticky(Message::deviceId)    // routing strategy (see below)
    .routes(8)                    // required; >= 2
    .factory(() -> Pipeline.<Message>builder()
            .executor(executor)
            .from(Buffer.of(Message.class))
            .to(msg -> process(msg)))
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

#### `OpenRouter<T, R>` — fan-out to one of N open arms, with fan-in

```java
OpenRouter.<Message, List<Message>>of()
    .sticky(Message::deviceId)
    .routes(16)
    .factory(() -> OpenPipeline.builder()
            .executor(executor)
            .from(Buffer.of(Message.class))
            .then(Group.of(Message.class, String.class)
                    .groupingFunction(Message::customerId))
            .build())
    .itemPostedHandler(...)
    .itemDeliveredHandler(...)
```

Use `OpenRouter` (not `Router`) when arm output must flow to a shared downstream stage.

**Routing strategies** (mutually exclusive; default is round-robin):

| Method                                 | Behavior                                                   |
|----------------------------------------|------------------------------------------------------------|
| `.roundRobin()`                        | Explicit round-robin (same as the default).                |
| `.balanced()`                          | Route to the arm with the fewest in-flight items.          |
| `.sticky(Function<T,?>)`               | Consistent hashing — same key always goes to the same arm. |
| `.routingFunction(RoutingFunction<T>)` | Custom; return a zero-based arm index.                     |

---

## SourceBlock — Async Producer

`SourceBlock<T>` is not a pipeline stage — it is an async producer that polls a supplier
on dedicated threads and pushes items downstream.  Build it directly and wire it via
`linkTo`:

```java
// Single-item mode — supplier returns one item or null (null is a no-op)
SourceBlock<Message> source = SourceBlock.<Message>builder()
        .supplier(() -> queue.poll())
        .executor(executor)
        .build();

// Batch mode — supplier returns a list; null/empty is a no-op; items forwarded individually
SourceBlock<Message> source = SourceBlock.<Message>builder()
        .batchSupplier(() -> queue.receiveBatch())
        .executor(executor)
        .build();

source.linkTo(pipeline);       // or source.linkTo(bufferBlock) — any Target<T>
```

Exactly one of `.supplier()` / `.batchSupplier()` is required; building with neither or
both throws `IllegalStateException`.

```java
// Fixed — N polling threads always active
.concurrencyPolicy(SourceConcurrencyPolicy.fixed(4))

// Adaptive — starts at minThreads, grows to maxThreads on consecutive non-empty returns,
// shrinks back to the floor on any empty return
.concurrencyPolicy(
        SourceConcurrencyPolicy.adaptive(8)   // maxThreads; must be >= 2
                .minThreads(2)                // floor; default 1
                .scaleUpThreshold(5))         // successes before adding a thread; default 10
```

When `maxThreads > 1` the downstream head block must be thread-safe.  Linking `SourceBlock`
directly to a `BufferBlock` is the standard pattern — `BufferBlock.post()` is thread-safe
and the internal `BlockingQueue` absorbs concurrent posts naturally.

Optional delegates: `.itemDeliveredHandler(...)`, `.errorOccurredHandler(...)`

`SourceBlock` has no `complete()` — stop it by shutting down its executor, then drain the
rest of the pipeline:

```java
executor.shutdown();          // interrupt polling threads
pipeline.complete();          // propagate completion head → tail
pipeline.awaitCompletion();   // wait for all in-flight items to drain
```

---

## Completion Lifecycle

```java
pipeline.complete();                              // signal no more items; cascades head → tail
pipeline.awaitCompletion();                       // block until fully drained
pipeline.awaitCompletion(Duration.ofSeconds(30)); // with timeout; returns true if drained
pipeline.completion()                             // CompletableFuture<Void>
        .thenRun(() -> log("done"));

executor.shutdown();   // after awaitCompletion() — interrupts blocked workers
```

---

## NamedExecutorService

```java
NamedExecutorService executor = NamedExecutorService.builder()
        .threadPrefix("pipeline-workers")   // prefix for all worker thread names; required
        .build();
```

Threads are created on demand (one per async stage worker) and cached for 60 seconds
after they become idle.  There is no pool-size limit to configure.

- `executor.poolSize()` — current number of live threads in the pool.
- `executor.activeCount()` — threads currently executing a task.
- `executor.shutdown()` — interrupts all worker threads (use after `awaitCompletion()`).

Any `Executor` is accepted by stage builders; `NamedExecutorService` is preferred because
its `shutdown()` unblocks workers that are waiting on an empty queue.

---

## Advanced / Manual Wiring

Use manual wiring when:
- You need to attach a pre-built block to a fluent chain (`() -> existingBlock`).
- You are building a dynamic topology at runtime.
- You are implementing a custom `Target<T>`, `Source<T>`, or `Stage<T,R>`.

### Core interfaces

```java
// software.frisby.core.concurrency

@FunctionalInterface
Target<T>              // receieve items: boolean post(T item)
                       // lifecycle:      complete(), awaitCompletion(), completion()

Source<T>              // emit items:     void linkTo(Target<T> target)

Stage<T, R>            // implements both Target<T> and Source<R>
```

### Direct block construction

```java
// Build a block directly from its static builder
BufferBlock<Message> buffer = BufferBlock.<Message>builder()
        .capacity(2048)
        .executor(executor)
        .errorOccurredHandler((source, item, error) -> deadLetter.send(item))
        .build();

GroupBlock<Message, String> grouper = GroupBlock.<Message, String>builder()
        .groupingFunction(Message::customerId)
        .maxGroupSize(128)
        .executor(executor)
        .build();

ActionBlock<List<Message>> action = ActionBlock.<List<Message>>builder()
        .action(batch -> persist(batch))
        .build();

// Wire manually
buffer.linkTo(grouper);
grouper.linkTo(action);

// Post and drain
buffer.post(message);
buffer.complete();
buffer.awaitCompletion();
executor.shutdown();
```

### Inline raw Target in a fluent chain

```java
// Pass any pre-built block as a PipelineTarget lambda
Pipeline<Order> pipeline = Pipeline.<Order>builder()
        .executor(executor)
        .from(Buffer.of(Order.class))
        .to(() -> existingActionBlock);    // PipelineTarget<Order> = () -> Target<Order>
```

### Implementing a custom Target

`Target<T>` is a `@FunctionalInterface` — `post(T item)` is the only required method.
Default methods (`size()`, `inFlight()`, `complete()`, `completion()`, `awaitCompletion()`)
are already implemented and suitable for synchronous targets.  Override only what you need:

```java
public class MetricsTarget<T> implements Target<T> {
    private final Target<T> delegate;
    private final Counter counter;

    public MetricsTarget(Target<T> delegate, Counter counter) {
        this.delegate = delegate;
        this.counter = counter;
    }

    @Override
    public boolean post(T item) {
        boolean accepted = this.delegate.post(item);
        if (accepted) {
            this.counter.increment();
        }

        return accepted;
    }

    @Override
    public void complete() {
        this.delegate.complete();
    }

    @Override
    public CompletableFuture<Void> completion() {
        return this.delegate.completion();
    }
}
```

### Available block builders (direct API)

| Block interface          | Builder entry point                |
|--------------------------|------------------------------------|
| `BufferBlock<T>`         | `BufferBlock.<T>builder()`         |
| `PriorityBufferBlock<T>` | `PriorityBufferBlock.<T>builder()` |
| `BatchBlock<T>`          | `BatchBlock.<T>builder()`          |
| `GroupBlock<T, K>`       | `GroupBlock.<T, K>builder()`       |
| `DelayBlock<T>`          | `DelayBlock.<T>builder()`          |
| `TapBlock<T>`            | `TapBlock.<T>builder()`            |
| `TransformBlock<T, R>`   | `TransformBlock.<T, R>builder()`   |
| `ExpandBlock<T>`         | `ExpandBlock.<T>builder()`         |
| `BranchBlock<T>`         | `BranchBlock.<T>builder()`         |
| `RouterBlock<T>`         | `RouterBlock.<T>builder()`         |
| `BroadcastBlock<T>`      | `BroadcastBlock.<T>builder()`      |
| `ActionBlock<T>`         | `ActionBlock.<T>builder()`         |
| `SourceBlock<T>`         | `SourceBlock.<T>builder()`         |
| `NamedExecutorService`   | `NamedExecutorService.builder()`   |

---

## Anti-Patterns

### ❌ Calling `.executor()` when no async stages are present

```java
// Wrong — executor is unnecessary; no async stages present
Pipeline<Order> p = Pipeline.<Order>builder()
        .executor(executor)
        .from(Transform.of(Order.class, Invoice.class)
                .transform(order -> toInvoice(order)))
        .to(invoice -> store(invoice));

// Correct — all stages sync; no executor needed
Pipeline<Order> p = Pipeline.<Order>builder()
        .from(Transform.of(Order.class, Invoice.class)
                .transform(order -> toInvoice(order)))
        .to(invoice -> store(invoice));
```

### ❌ Shutting down the executor before awaiting completion

```java
// Wrong — may interrupt workers before they drain
pipeline.complete();
executor.shutdown();           // ← workers killed before items finish processing

// Correct
pipeline.complete();
pipeline.awaitCompletion();    // drain first
executor.shutdown();
```

### ❌ Using `Router` when arm results must merge downstream

```java
// Wrong — Router arms terminate independently; output cannot be collected
.to(Router.of(Message.class)
        .routes(8)
        .factory(() -> Pipeline.builder()...))

// Correct — use OpenRouter to merge arm outputs at a single downstream stage
.then(OpenRouter.<Message, List<Message>>of()
        .routes(8)
        .factory(() -> OpenPipeline.builder()...))
.to(results -> process(results))
```

### ❌ Building a `Branch` without an `otherwise` target

```java
// Wrong — throws IllegalArgumentException at build time
.to(Branch.of(Order.class)
        .when(order -> order.isPriority(), priorityPipeline))

// Correct
.to(Branch.of(Order.class)
        .when(order -> order.isPriority(), priorityPipeline)
        .otherwise(standardPipeline))
```

### ❌ Reusing a fluent stage instance across multiple pipelines

```java
// Wrong — each Pipeline.builder() call materializes the block once;
// sharing the same Buffer instance in two chains produces unpredictable wiring.
Buffer<Message> shared = Buffer.of(Message.class);
Pipeline<Message> p1 = Pipeline.<Message>builder().executor(executor).from(shared).to(...);
Pipeline<Message> p2 = Pipeline.<Message>builder().executor(executor).from(shared).to(...);

// Correct — create a fresh fluent builder per pipeline
Pipeline<Message> p1 = Pipeline.<Message>builder().executor(executor).from(Buffer.of(Message.class)).to(...);
Pipeline<Message> p2 = Pipeline.<Message>builder().executor(executor).from(Buffer.of(Message.class)).to(...);
```

---

## Logging

All diagnostic output flows through a single logger: `software.frisby.core.concurrency.EventSource`.

| Level       | When it fires                                                                                                                                                                          |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **ERROR**   | Delivery failure, callback exception, predicate failure, supplier failure.                                                                                                             |
| **WARNING** | A block received an item but has no downstream target linked yet.  Fires at most once per posting thread before the first `linkTo()` call; never fires again after the block is wired. |

Recommended production threshold: **WARNING** — captures both delivery failures and the
unlinked-block diagnostic.  Set to **ERROR** if you prefer maximum silence.

```properties
# JUL — WARNING maps directly; ERROR maps to SEVERE
software.frisby.core.concurrency.EventSource.level = WARNING
```

```xml
<!-- Logback -->
<logger name="software.frisby.core.concurrency.EventSource" level="WARN"/>
```

```xml
<!-- Log4j2 -->
<Logger name="software.frisby.core.concurrency.EventSource" level="WARN" additivity="false"/>
```

