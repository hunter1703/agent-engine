# Session Actor Rebuild — Design Specification

## Overview

Replace the current monolithic `SessionActor` (~900 lines, flat state, accumulated history) with a composed, single-actor design that separates concerns through well-typed state composition, externalizes history via Pekko Projections, and replaces the MongoDB-heavy ADK session service with a lightweight projection-backed alternative.

### Goals

1. **Cognitive load**: every type is self-documenting, every state transition obvious from the code. The actor reads like a state machine specification.
2. **Operational robustness**: no unbounded history in actor state or snapshots. Bounded turn buffers. Decoupled history reads that don't depend on actor availability.
3. **Feature ceiling**: reusable child workers, run-level await, typed command/reply protocol, clean extension points.

### Constraints

- Pekko cluster sharding + event sourcing foundation is preserved.
- No backward compatibility required — clean slate, existing sessions do not survive migration.
- Live event stream model (broadcast channel per root session) is unchanged.
- Retries are ephemeral (reset on crash).

---

## 1. Session Topology & Identity

Session topology is established once at creation and is immutable.

### Types

```java
record SessionTopology(
    String sessionId,
    String agentId,
    SessionRole role
) {
    String rootSessionId() {
        return switch (role) {
            case SessionRole.Root _ -> sessionId;
            case SessionRole.Child c -> c.rootSessionId();
        };
    }

    boolean isRoot() {
        return role instanceof SessionRole.Root;
    }
}

sealed interface SessionRole {
    record Root() implements SessionRole {}
    record Child(
        String rootSessionId,
        String parentSessionId,
        String parentAgentId
    ) implements SessionRole {}
}
```

### Enforcement

- `SessionTopology` is a `final` field on the actor, assigned in the constructor, never reassigned.
- `SessionRole.Child` fields are all non-null (validated in the compact constructor).
- `SessionActorFactory` resolves topology at entity creation time from the `InitializeSession` command — the only command accepted before initialization.

### Actor lifecycle

Two-phase: uninitialized (accepts only `InitializeSession`) → initialized (accepts all other commands). `InitializeSession` persists `Fact.SessionInitialized` and transitions the actor to initialized state.

---

## 2. Execution Model

The sealed state machine replacing phase + scattered fields.

### Types

```java
sealed interface ExecutionState {

    MessageQueue queue();

    ExecutionState withQueue(MessageQueue queue);

    record Idle(
        MessageQueue queue
    ) implements ExecutionState {
        @Override
        public ExecutionState withQueue(MessageQueue queue) {
            return new Idle(queue);
        }
    }

    record Running(
        String runId,
        int retryCount,
        MessageQueue queue
    ) implements ExecutionState {
        @Override
        public ExecutionState withQueue(MessageQueue queue) {
            return new Running(runId, retryCount, queue);
        }
    }

    record Paused(
        String runId,
        Set<String> confirmationIds,
        ResumeTarget resumeTarget,
        MessageQueue queue
    ) implements ExecutionState {
        @Override
        public ExecutionState withQueue(MessageQueue queue) {
            return new Paused(runId, confirmationIds, resumeTarget, queue);
        }
    }
}

sealed interface ResumeTarget {
    record Self() implements ResumeTarget {}
    record Child(String childSessionId) implements ResumeTarget {}
}

record MessageQueue(List<String> messages) {
    static final MessageQueue EMPTY = new MessageQueue(List.of());

    MessageQueue enqueue(String message) {
        var next = new ArrayList<>(messages);
        next.add(message);
        return new MessageQueue(List.copyOf(next));
    }

    Optional<String> peek() {
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.getFirst());
    }

    MessageQueue dequeue() {
        return messages.isEmpty()
            ? this
            : new MessageQueue(List.copyOf(messages.subList(1, messages.size())));
    }

    boolean isEmpty() { return messages.isEmpty(); }
}
```

### Design decisions

- **`MessageQueue` is embedded in every state variant.** Every state transition must explicitly decide what happens to the queue — the compiler forces this.
- **`ResumeTarget` makes cascade semantics explicit.** `Self` means this actor owns the confirmation. `Child(id)` means forward the resume.
- **`retryCount` lives in `Running`, is ephemeral.** Not persisted — on recovery, retries start from 0. Crash resets retries. Maximum retry limit: `MAX_RETRIES = 3` (configurable via application properties).
- **No `Terminal` state.** A finished run returns to `Idle`. Pekko passivation handles idle actor cleanup.

### State transitions

```
Idle ──StartRun──► Running(runId, 0, queue)
Running ──ExecutionCompleted──► Idle(queue) [then auto-dequeue if non-empty]
Running ──PauseRequested(self)──► Paused(runId, ids, Self, queue)
Running ──ChildPauseCascaded──► Paused(runId, ids, Child(id), queue)
Running ──ExecutionFailed(recoverable)──► Running(runId, retryCount+1, queue) [if retryCount < MAX_RETRIES (3)]
Running ──ExecutionFailed(terminal)──► Idle(queue) [notify parent if child]
Paused ──ResumeRun──► Running(runId, 0, queue)
Any ──StartRun while busy──► same state with queue.enqueue(message)
```

---

## 3. Child Registry & Reusable Workers

### Types

```java
record ChildRegistry(Map<String, ChildWorker> workers) {

    static final ChildRegistry EMPTY = new ChildRegistry(Map.of());

    ChildRegistry register(String childSessionId, ChildWorker worker) {
        var next = new HashMap<>(workers);
        next.put(childSessionId, worker);
        return new ChildRegistry(Map.copyOf(next));
    }

    ChildRegistry update(String childSessionId, UnaryOperator<ChildWorker> fn) {
        var worker = workers.get(childSessionId);
        if (worker == null) return this;
        var next = new HashMap<>(workers);
        next.put(childSessionId, fn.apply(worker));
        return new ChildRegistry(Map.copyOf(next));
    }

    Optional<ChildWorker> get(String childSessionId) {
        return Optional.ofNullable(workers.get(childSessionId));
    }

    boolean hasActiveRuns() {
        return workers.values().stream().anyMatch(ChildWorker::hasActiveRun);
    }
}

record ChildWorker(
    String childAgentId,
    Map<String, ChildRun> runs
) {
    boolean hasActiveRun() {
        return runs.values().stream()
            .anyMatch(r -> r.state() instanceof ChildRunState.Active
                        || r.state() instanceof ChildRunState.Paused);
    }

    ChildWorker withRun(String runId, ChildRun run) {
        var next = new HashMap<>(runs);
        next.put(runId, run);
        return new ChildWorker(childAgentId, Map.copyOf(next));
    }
}

record ChildRun(
    String runId,
    ChildRunState state
) {
    ChildRun withState(ChildRunState newState) {
        return new ChildRun(runId, newState);
    }
}

sealed interface ChildRunState {
    record Active() implements ChildRunState {}
    record Paused(Set<String> confirmationIds) implements ChildRunState {}
    record Completed(ChildRunResult result) implements ChildRunState {}
    record Failed(String reason) implements ChildRunState {}
}

record ChildRunHandle(
    String childSessionId,
    String childRunId
) {}

record ChildRunResult(
    String output,
    Map<String, Object> metadata
) {
    static ChildRunResult of(String output) {
        return new ChildRunResult(output, Map.of());
    }
}
```

### Run handle semantics

When a parent spawns a child or sends a task, it gets back a `ChildRunHandle` identifying the specific run. `await_agent` awaits a specific run handle — two sequential `send_task` calls produce different handles, and awaiting one never resolves with the other's result.

### Await behavior

`pendingAwaitCallbacks` remains ephemeral (not persisted). Safety comes from the registry:

1. **Await before completion**: callback parked, delivered when `NotifyChildRunCompleted` arrives.
2. **Await after completion**: registry already has `Completed`, result returned immediately.
3. **Crash while parked**: callback lost. On recovery, registry is reconstructed from journal. If child completed, next `AwaitChildRun` gets immediate response. If not, child re-delivers notification on its own recovery.

The registry is the durable source of truth. The callback map is an optimization to avoid polling.

---

## 4. Persisted Facts

Design principle: persist coordination decisions and turn boundaries. No accumulated history in state.

### Types

```java
sealed interface Fact {

    // Session lifecycle
    record SessionInitialized(SessionTopology topology, Instant timestamp) implements Fact {}

    // Run lifecycle
    record RunStarted(String runId, String message, Instant timestamp) implements Fact {}
    record TurnCommitted(String runId, List<SessionEvent> events, long startSequence, Instant timestamp) implements Fact {}
    record RunPaused(String runId, Set<String> confirmationIds, ResumeTarget resumeTarget, Instant timestamp) implements Fact {}
    record RunResumed(String runId, String confirmationId, Instant timestamp) implements Fact {}
    record RunCompleted(String runId, Instant timestamp) implements Fact {}
    record RunFailed(String runId, String reason, boolean recoverable, Instant timestamp) implements Fact {}

    // Queue
    record MessageEnqueued(String message, Instant timestamp) implements Fact {}
    record MessageDequeued(Instant timestamp) implements Fact {}

    // Child lifecycle
    record ChildRegistered(String childSessionId, String childAgentId, Instant timestamp) implements Fact {}
    record ChildRunStarted(String childSessionId, String childRunId, Instant timestamp) implements Fact {}
    record ChildRunCompleted(String childSessionId, String childRunId, ChildRunResult result, Instant timestamp) implements Fact {}
    record ChildRunFailed(String childSessionId, String childRunId, String reason, Instant timestamp) implements Fact {}
    record ChildRunPaused(String childSessionId, String childRunId, Set<String> confirmationIds, Instant timestamp) implements Fact {}
}
```

### Design decisions

- **Explicit child terminal facts** (`ChildRunCompleted`, `ChildRunFailed`, `ChildRunPaused`) instead of a generic `ChildStateChanged`. Each carries a distinct payload, enables specific snapshot triggers, and reads clearly as a timeline.
- **`TurnCommitted` carries events + `startSequence`.** Sequence numbers are per-event, not per-turn. `startSequence` is the sequence of the first event in the batch; each subsequent event gets `startSequence + index`. The event handler advances `nextSequence` by `events.size()`. This gives every event a globally unique sequence within the session, which the projection uses as its idempotency key. Events are NOT stored in actor state.
- **`MessageEnqueued` / `MessageDequeued` are persisted.** The queue must survive recovery; relying on snapshots alone risks losing messages enqueued between snapshots.

### Event handler

Every fact maps to a pure function `(State, Fact) → State`. No side effects, no channel publishing, no runner calls. Side effects happen only in command handlers, after persistence.

### Serialization

All `Command`, `Fact`, and reply types must implement the project's `PekkoSerializable` marker interface. This ensures they are registered with the Pekko serialization framework. The existing Jackson-based Pekko serializer handles records natively.

### Snapshot strategy

Snapshot after: `RunCompleted`, `RunFailed`, `ChildRunCompleted`, `ChildRunFailed`. Journal retention: keep entries until the projection has consumed them, plus a configurable buffer. No aggressive deletion — the projection needs the journal.

---

## 5. History Projection & Session Service

**Terminology note:** This design uses two distinct "state" concepts:
- **`SessionState`** — the actor's composed state record (topology, execution, child registry, sequence counter). Managed by Pekko event sourcing.
- **Session variables** (`session.state()` in ADK) — the mutable `ConcurrentMap<String, Object>` that the ADK Runner reads and writes during a run (tool outputs, state deltas). Stored on the `AgentSession` MongoDB document. These are distinct from actor state.

### Two changes working together

1. **Pekko Projection** consumes `TurnCommitted` facts from the journal and writes `SessionEvent` records to a MongoDB `session_events` collection.
2. **`ProjectionBackedSessionService`** replaces `MongoSessionService` as the ADK session service, reading history from the projection instead of asking the actor.

### Projection handler

```java
final class SessionHistoryProjectionHandler
        extends Handler<EventEnvelope<Fact>> {

    private final MongoCollection<SessionEventRecord> collection;

    @Override
    public CompletionStage<Done> process(EventEnvelope<Fact> envelope) {
        return switch (envelope.event()) {
            case Fact.TurnCommitted fact -> writeTurnEvents(envelope.persistenceId(), fact);
            default -> CompletableFuture.completedFuture(Done.getInstance());
        };
    }

    private CompletionStage<Done> writeTurnEvents(String persistenceId, Fact.TurnCommitted fact) {
        var records = IntStream.range(0, fact.events().size())
            .mapToObj(i -> new SessionEventRecord(
                fact.events().get(i).sessionId(),
                fact.startSequence() + i,
                fact.events().get(i)))
            .toList();
        return bulkUpsert(records); // idempotent via unique index on (sessionId, sequence)
    }
}
```

**MongoDB schema:**

```java
record SessionEventRecord(
    String sessionId,
    long sequence,
    SessionEvent event
) {}
```

- Collection: `session_events`
- Unique index: `(sessionId, sequence)` — idempotency guarantee
- Query index: `(sessionId)` sorted by `sequence` ascending

**Projection lifecycle:**

- Runs as a cluster singleton (Pekko manages leader election)
- Offset tracked in `projection_offsets` MongoDB collection
- On startup, resumes from last committed offset
- Exactly-once delivery semantics with idempotent writes as safety net

### ProjectionBackedSessionService

Replaces `MongoSessionService`. The ADK `BaseSessionService.appendEvent` default method only mutates the in-memory `Session` object (appends to `session.events()`, merges `stateDelta` into `session.state()`). No MongoDB I/O per event.

```java
final class ProjectionBackedSessionService extends BaseSessionService {

    private final AgentSessionRepository sessionRepository;
    private final MongoCollection<SessionEventRecord> projectionCollection;

    @Override
    public Single<Session> createSession(String appName, String userId,
            ConcurrentMap<String, Object> state, String sessionId) {
        // Write AgentSession document to MongoDB (metadata, status, hierarchy)
        // Return fresh ADK Session with empty events list
    }

    @Override
    public Maybe<Session> getSession(String appName, String userId,
            String sessionId, Optional<GetSessionConfig> config) {
        // 1. Read AgentSession from MongoDB
        // 2. Read committed SessionEvents from projection collection
        // 3. Map SessionEvent → ADK Event
        // 4. Return hydrated ADK Session
        // No actor ask. No blocking CompletableFuture.join().
    }

    // appendEvent: NOT overridden.
    // Uses BaseSessionService default: in-memory mutation only.
}
```

### Session state durability

Instead of flushing `session.state()` to MongoDB on every non-partial event (current behavior), state is flushed once at turn boundary as a side effect of `TurnCommitted` persistence. This is more consistent: if events are discarded on crash, the state derived from them is also discarded — no state/history divergence.

### DefaultSessionHistory (rewritten)

```java
final class DefaultSessionHistory implements SessionHistory {

    private final MongoCollection<SessionEventRecord> collection;

    @Override
    public List<SessionEvent> events(String sessionId) {
        return collection
            .find(eq("sessionId", sessionId))
            .sort(ascending("sequence"))
            .map(SessionEventRecord::event)
            .into(new ArrayList<>());
    }
}
```

No actor ask. No blocking. Pure MongoDB read from the projection.

### Data flow

```
Runner.runAsync()
  │
  ├─ getSession() → ProjectionBackedSessionService
  │    ├─ reads AgentSession from MongoDB (state, metadata)
  │    └─ reads committed events from session_events collection
  │
  ├─ appendEvent(session, event) → default BaseSessionService
  │    └─ in-memory only: session.events().add(event), state merge
  │
  └─ DefaultAgentRunner tells SessionActor ExecutionEvent per event
       ├─ actor publishes to SessionEventChannel (live stream)
       ├─ actor adds to TurnBuffer
       └─ at turn boundary:
            ├─ persists TurnCommitted to journal
            ├─ projection writes events to session_events collection
            └─ actor flushes session state to AgentSession document
```

---

## 6. Command/Reply Protocol

Every command gets a dedicated reply type. The compiler enforces exhaustive handling.

### Commands

Commands are split into two sealed hierarchies: external (sent by callers outside the actor) and internal (sent by the runner, child actors, or the actor to itself). Both implement a common `Command` marker. This prevents external callers from accidentally sending internal commands.

```java
sealed interface Command permits ExternalCommand, InternalCommand {}

sealed interface ExternalCommand extends Command {
    record InitializeSession(SessionTopology topology, ActorRef<InitializeResult> replyTo) implements ExternalCommand {}
    record StartRun(String message, ActorRef<StartRunResult> replyTo) implements ExternalCommand {}
    record ResumeRun(String confirmationId, Object confirmationResponse, ActorRef<ResumeResult> replyTo) implements ExternalCommand {}
    record SpawnChild(String childAgentId, String message, ActorRef<SpawnResult> replyTo) implements ExternalCommand {}
    record SendChildTask(String childSessionId, String message, ActorRef<SendTaskResult> replyTo) implements ExternalCommand {}
    record AwaitChildRun(ChildRunHandle handle, ActorRef<AwaitResult> replyTo) implements ExternalCommand {}
}

sealed interface InternalCommand extends Command {
    // Child → parent notifications (fire-and-forget)
    record NotifyChildRunCompleted(String childSessionId, String childRunId, ChildRunResult result) implements InternalCommand {}
    record NotifyChildRunFailed(String childSessionId, String childRunId, String reason) implements InternalCommand {}
    record NotifyChildRunPaused(String childSessionId, String childRunId, Set<String> confirmationIds) implements InternalCommand {}

    // Runner → actor (fire-and-forget)
    record ExecutionEvent(String runId, SessionEvent event) implements InternalCommand {}
    record ExecutionCompleted(String runId) implements InternalCommand {}
    record ExecutionFailed(String runId, String error, boolean recoverable) implements InternalCommand {}
    record PauseRequested(String runId, Set<String> confirmationIds) implements InternalCommand {}

    // Self-messages
    record DrainQueue() implements InternalCommand {}
    record RecoveryCleanup(String runId) implements InternalCommand {}
}
```

The `SessionActorFactory.entityRef()` returns `EntityRef<ExternalCommand>` to callers. The actor's internal `ActorRef<Command>` (from `getContext().getSelf()`) accepts both hierarchies.

### Reply types

```java
sealed interface InitializeResult {
    record Initialized() implements InitializeResult {}
    record AlreadyInitialized() implements InitializeResult {}
}

sealed interface StartRunResult {
    record RunAccepted(String runId) implements StartRunResult {}
    record RunQueued(int position) implements StartRunResult {}
    record Rejected(String reason) implements StartRunResult {}
}

sealed interface ResumeResult {
    record Resumed(String runId) implements ResumeResult {}
    record Rejected(String reason) implements ResumeResult {}
}

sealed interface SpawnResult {
    record ChildSpawned(ChildRunHandle handle) implements SpawnResult {}
    record Rejected(String reason) implements SpawnResult {}
}

sealed interface SendTaskResult {
    record TaskAccepted(ChildRunHandle handle) implements SendTaskResult {}
    record Rejected(String reason) implements SendTaskResult {}
}

sealed interface AwaitResult {
    record Completed(ChildRunResult result) implements AwaitResult {}
    record Failed(String reason) implements AwaitResult {}
}
```

### Design decisions

- **`DrainQueue` replaces `StartRunFromQueue`.** Clearer name, no payload — queue is in the execution state.
- **Child notifications have no reply.** Fire-and-forget from the child's perspective. The child has persisted its own state; parent notification is best-effort.
- **`AwaitChildRun` uses deferred reply (standard Pekko async ask).** The `ActorRef<AwaitResult>` is parked in `pendingAwaitCallbacks` and replied to exactly once — either immediately (if the child already completed) or later (when `NotifyChildRunCompleted` arrives). There is no `Pending` variant; the caller blocks on the ask future until the result is available. This is the standard Pekko pattern for deferred responses.
- **`ExecutionEvent` has no reply.** High-frequency streaming path — reply-ack would create unnecessary backpressure.

---

## 7. Live Event Flow & Bounded Turn Buffer

### TurnBuffer

```java
final class TurnBuffer {

    private final List<SessionEvent> events;
    private final int capacity;

    static TurnBuffer create(int capacity) {
        return new TurnBuffer(new ArrayList<>(), capacity);
    }

    /** Returns true if buffer is full and should be flushed. */
    boolean add(SessionEvent event) {
        events.add(event);
        return events.size() >= capacity;
    }

    List<SessionEvent> drain() {
        var snapshot = List.copyOf(events);
        events.clear();
        return snapshot;
    }

    boolean isEmpty() { return events.isEmpty(); }
    int size() { return events.size(); }
}
```

Ephemeral mutable field on the actor — NOT part of `SessionState`, NOT included in snapshots. Reconstructed empty on recovery (committed events are in the projection; uncommitted events are intentionally lost per crash semantics). Default capacity ~500 events.

### Flow

```
ExecutionEvent arrives
  │
  ├─ publish to SessionEventChannel immediately (live stream, no batching)
  │
  └─ add to TurnBuffer
       ├─ not full → done
       └─ full → mid-turn commit:
              persist TurnCommitted(events, sequence)
              buffer.drain()
              increment sequence
              continue accepting events
```

At natural turn boundary (`ExecutionCompleted` or `PauseRequested`), final commit of whatever remains in the buffer.

### Channel completion predicate

```java
boolean shouldCompleteChannel() {
    return topology.isRoot()
        && execution() instanceof ExecutionState.Idle _
        && !childRegistry().hasActiveRuns();
}
```

Called after every state transition that could satisfy it.

---

## 8. Recovery Behavior

### Rule

A recovered `Running` state is always invalid. The in-flight execution died with the JVM.

### Recovery handler

In Pekko's `EventSourcedBehavior`, the recovery-completed signal handler cannot directly persist facts — only command handlers can produce `Effect().persist(...)`. The idiomatic approach is to self-send a command from the signal handler.

```java
// Internal command for recovery cleanup
record RecoveryCleanup(String runId) implements Command {}

void onRecoveryCompleted() {
    switch (state.execution()) {
        case ExecutionState.Running(var runId, _, var queue) -> {
            // Self-send to process through the normal command handler pipeline
            self().tell(new Command.RecoveryCleanup(runId));
        }
        case ExecutionState.Paused _ -> {
            // Valid — confirmations survive recovery. No action.
        }
        case ExecutionState.Idle _ -> {
            if (!state.execution().queue().isEmpty()) {
                self().tell(new Command.DrainQueue());
            }
        }
    }
}

// Command handler for RecoveryCleanup:
Effect<Fact, SessionState> onRecoveryCleanup(SessionState state, RecoveryCleanup cmd) {
    return Effect().persist(new Fact.RunFailed(cmd.runId(), "Interrupted by recovery", false, Instant.now()))
        .thenRun(newState -> {
            if (!newState.queue().isEmpty()) {
                self().tell(new Command.DrainQueue());
            }
        });
}
```

### Key decisions

- **`Running` → self-send `RecoveryCleanup` → persist `RunFailed` → `Idle`.** The self-send goes through the normal command handler pipeline, which can produce persistence effects. Since `RecoveryCleanup` is the first command processed after recovery, no user commands can interleave before it.
- **`Paused` survives recovery.** Confirmation IDs are in persisted state. External system sends `ResumeRun` when ready.
- **Auto-drain on `Idle` with non-empty queue.** Self-sends `DrainQueue` to process pending messages.

### Child recovery

Lazy reconciliation. The parent's `ChildRegistry` is reconstructed from journal facts. No reconciliation protocol with children. If a child completed while the parent was down and the notification was lost, the next `AwaitChildRun` call checks the registry:
- `Completed` → immediate response.
- `Active` → callback parked, child re-delivers on its own recovery or next command.

---

## 9. Actor Composition

### SessionState

```java
record SessionState(
    SessionTopology topology,
    ExecutionState execution,
    ChildRegistry childRegistry,
    long nextSequence
) {
    static SessionState initial(SessionTopology topology) {
        return new SessionState(topology, new ExecutionState.Idle(MessageQueue.EMPTY), ChildRegistry.EMPTY, 0L);
    }

    SessionState withExecution(ExecutionState execution) {
        return new SessionState(topology, execution, childRegistry, nextSequence);
    }

    SessionState withChildRegistry(ChildRegistry childRegistry) {
        return new SessionState(topology, execution, childRegistry, nextSequence);
    }

    SessionState withNextSequence(long nextSequence) {
        return new SessionState(topology, execution, childRegistry, nextSequence);
    }

    String rootSessionId()   { return topology.rootSessionId(); }
    boolean isRoot()         { return topology.isRoot(); }
    MessageQueue queue()     { return execution.queue(); }
}
```

### Actor structure

The actor class contains only:
- Command routing (phase-aware handler builder)
- Command handler methods (each follows the pattern: match execution state → validate → persist fact → side effects in callback)
- Recovery handler
- Snapshot trigger configuration

Event handler is a pure `(State, Fact) → State` mapping, compiler-verified exhaustive via sealed types.

### File organization

| File | Responsibility | ~Lines |
|------|----------------|--------|
| `SessionActor.java` | Command routing, command handlers, recovery | ~300 |
| `SessionState.java` | Composed state record with wither methods | ~40 |
| `SessionTopology.java` | `SessionTopology`, `SessionRole` | ~30 |
| `ExecutionState.java` | Sealed hierarchy, `MessageQueue`, `ResumeTarget` | ~80 |
| `ChildRegistry.java` | Registry, workers, runs, state, handles | ~100 |
| `SessionCommand.java` | All `Command` variants | ~80 |
| `SessionFact.java` | All `Fact` variants | ~80 |
| `SessionReply.java` | All reply sealed types | ~60 |
| `TurnBuffer.java` | Bounded ephemeral event buffer | ~30 |
| `SessionHistoryProjectionHandler.java` | Pekko Projection handler | ~50 |
| `ProjectionBackedSessionService.java` | Lightweight ADK session service | ~80 |
| `DefaultSessionHistory.java` | Reads from projection collection | ~30 |

### Deleted

| Current code | Replaced by |
|---|---|
| `MongoSessionService` | `ProjectionBackedSessionService` |
| `State.committedEvents` | Pekko Projection → MongoDB `session_events` |
| `Command.GetHistory` | Direct MongoDB query in `DefaultSessionHistory` |
| Phase enum | `ExecutionState` sealed type |
| Scattered child fields | `ChildRegistry` |
| `effectiveRoot()` null logic | `SessionTopology.rootSessionId()` |
| `SessionHistory.events(agentId, sessionId)` | Signature simplified to `events(sessionId)` — projection is keyed by sessionId only |

### Unchanged

| Component | Notes |
|---|---|
| `SessionEventChannel` | Same broadcast model, same scoping |
| `SessionActorFactory` | Updated for `InitializeSession`, same sharding |
| `AGUIEventMapper` | Same mapping, reads from projection |
| `SessionEvent` record | Same structure |
| `SessionEventKind` enum | Same classification; add `valueOfOrDefault` parser per project conventions |
| `SessionEventUtils` | Same utilities |

---

## 10. Test Plan

### Unit tests (pure state transitions)

- `ExecutionState` transitions: each transition function produces the expected variant with correct fields.
- `ChildRegistry` operations: register, update, get, `hasActiveRuns` across lifecycle states.
- `MessageQueue`: enqueue/dequeue/peek ordering, empty behavior.
- `SessionState` event handler: each `Fact` variant produces the correct state mutation. Exhaustive — one test per fact type.
- `TurnBuffer`: bounded capacity, drain returns correct events and clears, add returns true at capacity.

### Actor integration tests

- **Root run queueing**: second `StartRun` while running returns `RunQueued`; auto-starts after active run completes.
- **Self pause/resume**: `PauseRequested` transitions to `Paused(Self)`, `ResumeRun` transitions back to `Running`, runner resumes.
- **Cascaded child pause**: child HITL → parent receives `NotifyChildRunPaused` → parent transitions to `Paused(Child(id))` → root `ResumeRun` forwards down the chain to the leaf.
- **Reusable child worker**: one child session handles multiple `SendChildTask` runs; each gets distinct `ChildRunHandle`; awaiting one never resolves with another's result.
- **Late await**: `AwaitChildRun` after child completion returns cached result immediately.
- **Failure and retry**: recoverable failure retries up to limit; terminal failure commits to `Idle`, notifies parent.
- **Recovery from `Running`**: restart mid-run → persists `RunFailed` → state is `Idle` → queue drains.
- **Recovery from `Paused`**: restart while paused → state remains `Paused` → `ResumeRun` still works.
- **Channel completion**: root channel closes only when root is `Idle` AND no child has active runs.

### Projection tests

- `TurnCommitted` facts produce correct `SessionEventRecord` documents in MongoDB.
- Idempotent: replaying the same fact does not create duplicates.
- Ordering: events are queryable by `(sessionId, sequence)` in correct order.
- `DefaultSessionHistory.events()` returns complete history from projection.

### Session service tests

- `ProjectionBackedSessionService.getSession()` returns session hydrated with projection events.
- `appendEvent` (default) only mutates in-memory session — no MongoDB writes.
- `createSession` writes `AgentSession` document to MongoDB.

### End-to-end tests

- Full run lifecycle: start → stream events → commit → replay from projection matches live stream.
- Multi-agent: parent spawns child, sends task, awaits, child completes, parent resumes — full flow with typed handles.
- HITL cascade: root → child → grandchild pause → resume propagates correctly.
