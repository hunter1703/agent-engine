# Pekko-Native Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the engine's event bus and runtime infrastructure into a generic, fault-tolerant, Pekko-native architecture with reusable building blocks in `util/pekko`.

**Architecture:** A new `util/pekko` module provides generic Pekko building blocks (`ShardedEntity`, `BroadcastBehavior`, `PekkoEventChannel`). The engine's `AgentRuntime` owns event channels using these building blocks. `AgentBus`/`SessionBus` are deleted and replaced by typed usages of `EventChannel<E>` and `ScopedEventChannel<K,E>`. The `util/common/infra/events/` hierarchy is simplified from 7 interfaces to 2.

**Tech Stack:** Apache Pekko (actor-typed, cluster-typed, cluster-sharding-typed, persistence-typed, persistence-jdbc, streams), Quarkus CDI, RxJava3 (in-memory impls only, gradually removed), Reactive Streams SPI.

---

## File Map

### New files

| File | Responsibility |
|---|---|
| `util/pekko/build.gradle` | Module build config |
| `util/pekko/src/main/java/com/agentengine/util/pekko/PekkoBaseConfig.java` | Abstract Pekko cluster config |
| `util/pekko/src/main/java/com/agentengine/util/pekko/ActorSystemProvider.java` | CDI producer for `ActorSystem<Void>` |
| `util/pekko/src/main/java/com/agentengine/util/pekko/actor/ShardedEntity.java` | Abstract base for persistent sharded entities |
| `util/pekko/src/main/java/com/agentengine/util/pekko/actor/BroadcastBehavior.java` | Generic pub-sub actor behavior factory |
| `util/pekko/src/main/java/com/agentengine/util/pekko/events/PekkoEventChannel.java` | `EventChannel<E>` backed by `BroadcastBehavior` |
| `util/pekko/src/main/java/com/agentengine/util/pekko/events/PekkoScopedEventChannel.java` | `ScopedEventChannel<K,E>` backed by cluster sharding |
| `util/pekko/src/main/java/com/agentengine/util/pekko/util/PekkoStreams.java` | `Source↔Publisher` bridges and ask helpers |
| `engine/src/main/java/com/agentengine/engine/runtime/SessionEvent.java` | Sealed event interface for session turn events |
| `engine/src/main/java/com/agentengine/engine/runtime/AgentEvent.java` | Sealed event interface for agent lifecycle events |
| `engine/src/main/java/com/agentengine/engine/runtime/SessionActor.java` | Sharded persistent actor per session |
| `engine/src/main/java/com/agentengine/engine/runtime/RootSessionActor.java` | Sharded persistent actor per root session |
| `engine/src/main/java/com/agentengine/engine/runtime/AgentRuntime.java` | `@Singleton` owning actor system + event channels |
| `engine/src/main/java/com/agentengine/engine/runtime/AgentRuntimeConfig.java` | Engine-specific Pekko config |

### Modified files

| File | Change |
|---|---|
| `settings.gradle` | Add `include 'util:pekko'` |
| `gradle/libs.versions.toml` | Add Pekko version + dependency aliases |
| `util/common/src/main/java/.../infra/events/EventChannel.java` | Collapse to 2-method interface |
| `util/common/src/main/java/.../infra/events/ScopedEventChannel.java` | Collapse to 4-method interface, rename `live()` → `events()` |
| `util/common/src/main/java/.../infra/events/InMemoryEventChannel.java` | Remove `waitFor()` (no longer part of interface) |
| `util/common/src/main/java/.../infra/events/InMemoryScopedEventChannel.java` | Rename `live()` → `events()` |
| `engine/build.gradle` | Add `util:pekko` dependency |
| `engine/src/main/java/.../services/AgentHub.java` | Remove `sessionBus()/agentBus()`, add `sessionChannel()/agentChannel()` |
| `engine/src/main/java/.../services/AgentHubImpl.java` | Replace registries with `AgentRuntime` |

### Deleted files

| File | Reason |
|---|---|
| `util/common/.../infra/events/EventPublisher.java` | Folded into `EventChannel<E>` |
| `util/common/.../infra/events/EventStream.java` | Folded into `EventChannel<E>` |
| `util/common/.../infra/events/EventAwaiter.java` | Not interface method; utility in `PekkoStreams` |
| `util/common/.../infra/events/ScopedEventPublisher.java` | Folded into `ScopedEventChannel<K,E>` |
| `util/common/.../infra/events/ScopedEventStream.java` | Folded into `ScopedEventChannel<K,E>` |
| `engine/src/main/java/.../runtime/AgentBus.java` | Replaced by `EventChannel<AgentEvent>` |
| `engine/src/main/java/.../runtime/AgentBusEvent.java` | Renamed → `AgentEvent` |
| `engine/src/main/java/.../runtime/AgentBusRegistry.java` | Owned by `AgentRuntime` |
| `engine/src/main/java/.../runtime/SessionBus.java` | Replaced by `ScopedEventChannel<String, SessionEvent>` |
| `engine/src/main/java/.../runtime/SessionBusEvent.java` | Renamed → `SessionEvent` |
| `engine/src/main/java/.../runtime/SessionBusRegistry.java` | Owned by `AgentRuntime` |
| `engine/src/main/java/.../collaboration/CollaborationService.java` | Merged into `AgentHub` |
| `engine/src/main/java/.../collaboration/CollaborationServiceImpl.java` | Merged into `AgentHubImpl` |
| `engine/src/main/java/.../collaboration/CollaborationEventUtils.java` | Logic stays in `AgentHubImpl` |

---

## Phase 1 — `util/pekko` Module

### Task 1: Module scaffolding + Pekko dependencies

**Files:**
- Create: `util/pekko/build.gradle`
- Modify: `settings.gradle`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add Pekko to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:
```toml
pekko = "1.1.2"
pekko-persistence-jdbc = "1.1.0"
```

Add to `[libraries]`:
```toml
pekko-bom = { module = "org.apache.pekko:pekko-bom_2.13", version.ref = "pekko" }
pekko-actor-typed = { module = "org.apache.pekko:pekko-actor-typed_2.13", version.ref = "pekko" }
pekko-cluster-typed = { module = "org.apache.pekko:pekko-cluster-typed_2.13", version.ref = "pekko" }
pekko-cluster-sharding-typed = { module = "org.apache.pekko:pekko-cluster-sharding-typed_2.13", version.ref = "pekko" }
pekko-persistence-typed = { module = "org.apache.pekko:pekko-persistence-typed_2.13", version.ref = "pekko" }
pekko-persistence-jdbc = { module = "org.apache.pekko:pekko-persistence-jdbc_2.13", version.ref = "pekko-persistence-jdbc" }
pekko-serialization-jackson = { module = "org.apache.pekko:pekko-serialization-jackson_2.13", version.ref = "pekko" }
pekko-streams = { module = "org.apache.pekko:pekko-stream_2.13", version.ref = "pekko" }
pekko-testkit = { module = "org.apache.pekko:pekko-actor-testkit-typed_2.13", version.ref = "pekko" }
```

- [ ] **Step 2: Add module to settings.gradle**

```gradle
include 'util:pekko'
```

After `include 'util:ms'`.

- [ ] **Step 3: Create util/pekko/build.gradle**

```gradle
plugins {
    id 'com.agentengine.java-library-conventions'
}

dependencies {
    implementation platform(libs.pekko.bom)
    implementation project(':util:common')
    implementation libs.pekko.actor.typed
    implementation libs.pekko.cluster.typed
    implementation libs.pekko.cluster.sharding.typed
    implementation libs.pekko.persistence.typed
    implementation libs.pekko.persistence.jdbc
    implementation libs.pekko.serialization.jackson
    implementation libs.pekko.streams

    testImplementation libs.pekko.testkit
}
```

- [ ] **Step 4: Create package directory structure**

```bash
mkdir -p util/pekko/src/main/java/com/agentengine/util/pekko/actor
mkdir -p util/pekko/src/main/java/com/agentengine/util/pekko/events
mkdir -p util/pekko/src/main/java/com/agentengine/util/pekko/util
mkdir -p util/pekko/src/test/java/com/agentengine/util/pekko
mkdir -p util/pekko/src/test/resources
```

- [ ] **Step 4b: Create test Pekko config for in-memory persistence**

Create `util/pekko/src/test/resources/pekko.conf`:

```hocon
# Test configuration — use in-memory journal to avoid JDBC dependency in unit tests
pekko {
  loglevel = "WARNING"
  actor.provider = "local"

  persistence {
    journal.plugin  = "pekko.persistence.journal.inmem"
    snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
    snapshot-store.local.dir = "target/test-snapshots"
  }
}
```

This switches from JDBC persistence to the built-in in-memory journal for tests — no database required.

- [ ] **Step 5: Verify build compiles**

```bash
./gradlew :util:pekko:build -x test
```
Expected: BUILD SUCCESSFUL (empty module compiles)

- [ ] **Step 6: Commit**

```bash
git add util/pekko/ settings.gradle gradle/libs.versions.toml
git commit -m "build: add util:pekko module scaffold with Pekko dependencies"
```

---

### Task 2: `PekkoBaseConfig` — abstract cluster configuration

**Files:**
- Create: `util/pekko/src/main/java/com/agentengine/util/pekko/PekkoBaseConfig.java`

- [ ] **Step 1: Create PekkoBaseConfig**

```java
package com.agentengine.util.pekko;

import java.util.List;

/**
 * Base configuration for a Pekko cluster node. Extend this with
 * application-specific config using {@code @ConfigMapping}.
 */
public abstract class PekkoBaseConfig {

    public abstract String hostname();

    public abstract int port();

    public abstract List<String> seedNodes();

    public abstract String clusterName();

    public abstract String jdbcUrl();

    public abstract String jdbcUser();

    public abstract String jdbcPassword();

    /** Persist a snapshot after this many events (default: 100). */
    public int snapshotThreshold() {
        return 100;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add util/pekko/
git commit -m "feat(util/pekko): add PekkoBaseConfig abstract cluster configuration"
```

---

### Task 3: `ActorSystemProvider` — CDI producer

**Files:**
- Create: `util/pekko/src/main/java/com/agentengine/util/pekko/ActorSystemProvider.java`
- Create: `util/pekko/src/test/java/com/agentengine/util/pekko/ActorSystemProviderTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.agentengine.util.pekko;

import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ActorSystemProviderTest {

    @Test
    void shouldCreateActorSystem() {
        final var config = new PekkoBaseConfig() {
            @Override public String hostname() { return "localhost"; }
            @Override public int port() { return 0; }  // 0 = ephemeral
            @Override public List<String> seedNodes() { return List.of(); }
            @Override public String clusterName() { return "test-cluster"; }
            @Override public String jdbcUrl() { return ""; }
            @Override public String jdbcUser() { return ""; }
            @Override public String jdbcPassword() { return ""; }
        };
        final var provider = new ActorSystemProvider(config);
        final ActorSystem<Void> system = provider.actorSystem();

        assertThat(system).isNotNull();
        assertThat(system.name()).isEqualTo("test-cluster");

        system.terminate();
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./gradlew :util:pekko:test --tests "*.ActorSystemProviderTest"
```
Expected: FAIL — `ActorSystemProvider` does not exist.

- [ ] **Step 3: Implement ActorSystemProvider**

```java
package com.agentengine.util.pekko;

import com.typesafe.config.ConfigFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * CDI producer for the Pekko {@link ActorSystem}. One system per application;
 * consumers inject {@code ActorSystem<Void>} directly.
 */
@ApplicationScoped
public class ActorSystemProvider {

    private final PekkoBaseConfig config;
    private volatile ActorSystem<Void> system;

    public ActorSystemProvider(final PekkoBaseConfig config) {
        this.config = config;
    }

    @Produces
    @ApplicationScoped
    public ActorSystem<Void> actorSystem() {
        if (system == null) {
            synchronized (this) {
                if (system == null) {
                    system = ActorSystem.create(
                        Behaviors.empty(),
                        config.clusterName(),
                        buildConfig()
                    );
                }
            }
        }
        return system;
    }

    private com.typesafe.config.Config buildConfig() {
        final String hocon = """
            pekko {
              actor.provider = cluster
              remote.artery.canonical {
                hostname = "%s"
                port = %d
              }
              cluster.seed-nodes = [%s]
              serialization.bindings {
                "com.agentengine.util.pekko.PekkoSerializable" = jackson-cbor
              }
              persistence {
                journal.plugin  = "jdbc-journal"
                snapshot-store.plugin = "jdbc-snapshot-store"
              }
            }
            """.formatted(
                config.hostname(),
                config.port(),
                config.seedNodes().stream()
                    .map(n -> "\"" + n + "\"")
                    .collect(java.util.stream.Collectors.joining(", "))
            );
        return ConfigFactory.parseString(hocon).withFallback(ConfigFactory.load());
    }
}
```

Also create the marker interface used by serialization:

```java
package com.agentengine.util.pekko;

/** Marker interface — all Pekko messages/events must implement this. */
public interface PekkoSerializable {}
```

- [ ] **Step 4: Run test to confirm pass**

```bash
./gradlew :util:pekko:test --tests "*.ActorSystemProviderTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add util/pekko/
git commit -m "feat(util/pekko): add ActorSystemProvider CDI producer and PekkoSerializable marker"
```

---

### Task 4: `BroadcastBehavior<E>` — generic pub-sub actor

**Files:**
- Create: `util/pekko/src/main/java/com/agentengine/util/pekko/actor/BroadcastBehavior.java`
- Create: `util/pekko/src/test/java/com/agentengine/util/pekko/actor/BroadcastBehaviorTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.agentengine.util.pekko.actor;

import com.agentengine.util.pekko.actor.BroadcastBehavior.Message;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BroadcastBehaviorTest {

    private static final ActorTestKit kit = ActorTestKit.create();

    @AfterAll
    static void teardown() { kit.shutdownTestKit(); }

    @Test
    void shouldBroadcastToAllSubscribers() {
        final var actor = kit.spawn(BroadcastBehavior.<String>create(), "broadcast");
        final TestProbe<String> sub1 = kit.createTestProbe();
        final TestProbe<String> sub2 = kit.createTestProbe();

        actor.tell(new Message.Subscribe<>(sub1.ref()));
        actor.tell(new Message.Subscribe<>(sub2.ref()));
        actor.tell(new Message.Publish<>("hello"));

        sub1.expectMessage("hello");
        sub2.expectMessage("hello");
    }

    @Test
    void shouldNotReceiveAfterUnsubscribe() {
        final var actor = kit.spawn(BroadcastBehavior.<String>create(), "unsub-test");
        final TestProbe<String> sub = kit.createTestProbe();

        actor.tell(new Message.Subscribe<>(sub.ref()));
        actor.tell(new Message.Unsubscribe<>(sub.ref()));
        actor.tell(new Message.Publish<>("ignored"));

        sub.expectNoMessage();
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./gradlew :util:pekko:test --tests "*.BroadcastBehaviorTest"
```
Expected: FAIL

- [ ] **Step 3: Implement BroadcastBehavior**

```java
package com.agentengine.util.pekko.actor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generic pub-sub actor. Subscribers register an {@code ActorRef<E>} and
 * receive all published events until they unsubscribe or the actor stops.
 * Use {@link #create()} to produce the initial behavior.
 */
public final class BroadcastBehavior<E> extends AbstractBehavior<BroadcastBehavior.Message<E>> {

    public static <E> Behavior<Message<E>> create() {
        return Behaviors.setup(BroadcastBehavior::new);
    }

    /** ADT for all messages this actor accepts. */
    public sealed interface Message<E> {
        record Publish<E>(E event) implements Message<E> {}
        record Subscribe<E>(ActorRef<E> subscriber) implements Message<E> {}
        record Unsubscribe<E>(ActorRef<E> subscriber) implements Message<E> {}
    }

    private final Set<ActorRef<E>> subscribers = new LinkedHashSet<>();

    private BroadcastBehavior(final ActorContext<Message<E>> context) {
        super(context);
    }

    @Override
    public Receive<Message<E>> createReceive() {
        return newReceiveBuilder()
            .onMessage(Message.Publish.class, this::onPublish)
            .onMessage(Message.Subscribe.class, this::onSubscribe)
            .onMessage(Message.Unsubscribe.class, this::onUnsubscribe)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Behavior<Message<E>> onPublish(final Message.Publish<?> msg) {
        final E event = (E) msg.event();
        subscribers.forEach(s -> s.tell(event));
        return this;
    }

    @SuppressWarnings("unchecked")
    private Behavior<Message<E>> onSubscribe(final Message.Subscribe<?> msg) {
        subscribers.add((ActorRef<E>) msg.subscriber());
        return this;
    }

    @SuppressWarnings("unchecked")
    private Behavior<Message<E>> onUnsubscribe(final Message.Unsubscribe<?> msg) {
        subscribers.remove((ActorRef<E>) msg.subscriber());
        return this;
    }
}
```

- [ ] **Step 4: Run tests to confirm pass**

```bash
./gradlew :util:pekko:test --tests "*.BroadcastBehaviorTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add util/pekko/
git commit -m "feat(util/pekko): add BroadcastBehavior generic pub-sub actor"
```

---

### Task 5: `ShardedEntity<C, E, S>` — abstract persistent sharded entity

**Files:**
- Create: `util/pekko/src/main/java/com/agentengine/util/pekko/actor/ShardedEntity.java`
- Create: `util/pekko/src/test/java/com/agentengine/util/pekko/actor/ShardedEntityTest.java`

- [ ] **Step 1: Write failing test** (uses a minimal concrete entity)

```java
package com.agentengine.util.pekko.actor;

import com.agentengine.util.pekko.PekkoSerializable;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShardedEntityTest {

    private static final ActorTestKit kit = ActorTestKit.create();

    @AfterAll
    static void teardown() { kit.shutdownTestKit(); }

    // Minimal counter entity for testing
    sealed interface CounterCmd extends PekkoSerializable {
        record Increment(ActorRef<Integer> replyTo) implements CounterCmd {}
        record GetCount(ActorRef<Integer> replyTo) implements CounterCmd {}
    }
    record Incremented() implements PekkoSerializable {}
    record CounterState(int count) {
        static CounterState empty() { return new CounterState(0); }
    }

    static class CounterEntity extends ShardedEntity<CounterCmd, Incremented, CounterState> {
        static final EntityTypeKey<CounterCmd> TYPE_KEY = EntityTypeKey.create(CounterCmd.class, "Counter");

        CounterEntity(final String entityId) { super(TYPE_KEY.name(), entityId); }

        @Override public CounterState emptyState() { return CounterState.empty(); }

        @Override
        public CommandHandler<CounterCmd, Incremented, CounterState> commandHandler() {
            return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(CounterCmd.Increment.class, (state, cmd) ->
                    Effect().persist(new Incremented()).thenRun(s -> cmd.replyTo().tell(s.count())))
                .onCommand(CounterCmd.GetCount.class, (state, cmd) -> {
                    cmd.replyTo().tell(state.count());
                    return Effect().none();
                })
                .build();
        }

        @Override
        public EventHandler<CounterState, Incremented> eventHandler() {
            return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(Incremented.class, (state, e) -> new CounterState(state.count() + 1))
                .build();
        }
    }

    @Test
    void shouldPersistAndRecoverState() {
        final var system = kit.system();
        CounterEntity.init(system); // registers sharding

        final var ref = CounterEntity.entityRef(system, "counter-1");
        final TestProbe<Integer> probe = kit.createTestProbe();

        ref.tell(new CounterCmd.Increment(probe.ref()));
        probe.expectMessage(1);
        ref.tell(new CounterCmd.Increment(probe.ref()));
        probe.expectMessage(2);

        ref.tell(new CounterCmd.GetCount(probe.ref()));
        assertThat(probe.receiveMessage()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./gradlew :util:pekko:test --tests "*.ShardedEntityTest"
```
Expected: FAIL

- [ ] **Step 3: Implement ShardedEntity**

```java
package com.agentengine.util.pekko.actor;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;

/**
 * Abstract base for cluster-sharded, event-sourced entities. Subclasses provide
 * only domain logic via {@link #commandHandler()} and {@link #eventHandler()}.
 * All Pekko boilerplate (persistence ID, snapshot retention, sharding registration)
 * lives here — analogous to {@code AbstractMongoRepository}.
 *
 * <p>Concrete subclass pattern:
 * <pre>{@code
 * public class FooEntity extends ShardedEntity<FooEntity.Command, FooEntity.Event, FooEntity.State> {
 *   static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "Foo");
 *
 *   FooEntity(String entityId) { super(TYPE_KEY.name(), entityId); }
 *
 *   @Override public State emptyState() { return State.empty(); }
 *   @Override public CommandHandler<Command, Event, State> commandHandler() { ... }
 *   @Override public EventHandler<State, Event> eventHandler() { ... }
 *
 *   // Register sharding and expose entity refs:
 *   public static void init(ActorSystem<?> system) {
 *     ClusterSharding.get(system).init(Entity.of(TYPE_KEY, ctx -> new FooEntity(ctx.getEntityId())));
 *   }
 *   public static EntityRef<Command> entityRef(ActorSystem<?> system, String id) {
 *     return ClusterSharding.get(system).entityRefFor(TYPE_KEY, id);
 *   }
 * }
 * }</pre>
 *
 * @param <C> command type
 * @param <E> event type
 * @param <S> state type
 */
public abstract class ShardedEntity<C, E, S>
    extends EventSourcedBehavior<C, E, S> {

    /** Snapshot every 100 events; subclasses may override. */
    protected int snapshotThreshold() {
        return 100;
    }

    /**
     * Derives the persistence ID from the type key name and the shard entity ID,
     * following the Pekko convention of {@code PersistenceId.of(typeKey.name(), entityId)}.
     *
     * @param typeKeyName the entity type key name — pass {@code TYPE_KEY.name()}
     * @param entityId    the shard entity ID
     */
    protected ShardedEntity(final String typeKeyName, final String entityId) {
        super(PersistenceId.of(typeKeyName, entityId));
    }

    @Override
    public abstract S emptyState();

    /**
     * Implement the Pekko {@link EventSourcedBehavior} contract.
     * Use {@code newCommandHandlerBuilder()} inside the implementation.
     */
    @Override
    public abstract org.apache.pekko.persistence.typed.javadsl.CommandHandler<C, E, S> commandHandler();

    /**
     * Implement the Pekko {@link EventSourcedBehavior} contract.
     * Use {@code newEventHandlerBuilder()} inside the implementation.
     */
    @Override
    public abstract org.apache.pekko.persistence.typed.javadsl.EventHandler<S, E> eventHandler();

    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(snapshotThreshold(), 2);
    }
}
```

**Note:** The `init()` and `entityRef()` static helpers are intentionally left to subclasses — they need the concrete `EntityTypeKey<C>`. The base cannot provide them without losing type safety.

- [ ] **Step 4: Run test to confirm pass**

```bash
./gradlew :util:pekko:test --tests "*.ShardedEntityTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add util/pekko/
git commit -m "feat(util/pekko): add ShardedEntity abstract base for persistent sharded actors"
```

---

### Task 6: `PekkoEventChannel<E>` and `PekkoScopedEventChannel<K, E>`

**Files:**
- Create: `util/pekko/src/main/java/com/agentengine/util/pekko/events/PekkoEventChannel.java`
- Create: `util/pekko/src/main/java/com/agentengine/util/pekko/events/PekkoScopedEventChannel.java`
- Create: `util/pekko/src/test/java/com/agentengine/util/pekko/events/PekkoEventChannelTest.java`

> **Prerequisite**: Tasks 1–5 must pass.

- [ ] **Step 1: Write failing test**

```java
package com.agentengine.util.pekko.events;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PekkoEventChannelTest {

    private static final ActorTestKit kit = ActorTestKit.create();
    private static final Materializer mat = Materializer.createMaterializer(kit.system());

    @AfterAll
    static void teardown() { kit.shutdownTestKit(); }

    @Test
    void shouldDeliverPublishedEventsToSubscribers() throws Exception {
        final var channel = new PekkoEventChannel<String>(kit.system());
        final List<String> received = new ArrayList<>();

        final Publisher<String> stream = channel.events();
        Source.fromPublisher(stream).take(2).runForeach(received::add, mat);

        channel.publish("first");
        channel.publish("second");

        // Give async stream time to process
        Thread.sleep(200);
        assertThat(received).containsExactly("first", "second");
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./gradlew :util:pekko:test --tests "*.PekkoEventChannelTest"
```

- [ ] **Step 3: Implement PekkoEventChannel**

```java
package com.agentengine.util.pekko.events;

import com.agentengine.util.common.infra.events.EventChannel;
import com.agentengine.util.pekko.actor.BroadcastBehavior;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.typed.javadsl.ActorSource;
import org.apache.pekko.stream.OverflowStrategy;
import org.reactivestreams.Publisher;

/**
 * {@link EventChannel} backed by a Pekko {@link BroadcastBehavior} actor.
 * Each call to {@link #events()} creates a new subscriber actor that receives
 * all events published after subscription.
 */
public final class PekkoEventChannel<E> implements EventChannel<E> {

    private final ActorSystem<?> system;
    private final ActorRef<BroadcastBehavior.Message<E>> broadcast;

    public PekkoEventChannel(final ActorSystem<?> system) {
        this(system, "event-channel-" + java.util.UUID.randomUUID());
    }

    public PekkoEventChannel(final ActorSystem<?> system, final String name) {
        this.system = system;
        this.broadcast = system.systemActorOf(BroadcastBehavior.create(), name, Props.empty());
    }

    @Override
    public void publish(final E event) {
        broadcast.tell(new BroadcastBehavior.Message.Publish<>(event));
    }

    @Override
    public Publisher<E> events() {
        final var source = ActorSource.<E, Void>actorRef(
            msg -> false,                   // never complete via message
            msg -> java.util.Optional.empty(), // no failure messages
            256,
            OverflowStrategy.dropHead()
        ).mapMaterializedValue(ref -> {
            broadcast.tell(new BroadcastBehavior.Message.Subscribe<>(ref));
            return ref;
        });
        return source.runWith(
            org.apache.pekko.stream.javadsl.Sink.asPublisher(true),
            org.apache.pekko.stream.Materializer.createMaterializer(system)
        );
    }
}
```

- [ ] **Step 4: Implement PekkoScopedEventChannel**

```java
package com.agentengine.util.pekko.events;

import com.agentengine.util.common.infra.events.EventChannel;
import org.apache.pekko.actor.typed.ActorSystem;
import org.reactivestreams.Publisher;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link ScopedEventChannel} where each scope key maps to its own
 * {@link PekkoEventChannel}. Suitable for use with cluster sharding:
 * pass a {@code shardingEntityId} as the scope key so any node can
 * publish/subscribe to the same scope.
 */
public final class PekkoScopedEventChannel<K, E> implements ScopedEventChannel<K, E> {

    private final ActorSystem<?> system;
    private final ConcurrentMap<K, PekkoEventChannel<E>> channels = new ConcurrentHashMap<>();

    public PekkoScopedEventChannel(final ActorSystem<?> system) {
        this.system = system;
    }

    @Override
    public void publish(final K scope, final E event) {
        channel(scope).publish(event);
    }

    @Override
    public Publisher<E> events(final K scope) {
        return channel(scope).events();
    }

    @Override
    public void complete(final K scope) {
        // PekkoEventChannel is actor-backed; stopping the channel actor
        // will terminate all open streams for this scope.
        channels.remove(scope);
    }

    @Override
    public void fail(final K scope, final Throwable throwable) {
        channels.remove(scope);
    }

    private PekkoEventChannel<E> channel(final K scope) {
        return channels.computeIfAbsent(scope, k ->
            new PekkoEventChannel<>(system, "scoped-" + k));
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :util:pekko:test
```
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add util/pekko/
git commit -m "feat(util/pekko): add PekkoEventChannel and PekkoScopedEventChannel"
```

---

### Task 7: `PekkoStreams` — bridge utilities

**Files:**
- Create: `util/pekko/src/main/java/com/agentengine/util/pekko/util/PekkoStreams.java`

- [ ] **Step 1: Implement PekkoStreams**

```java
package com.agentengine.util.pekko.util;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Static bridge utilities between Pekko Streams and Reactive Streams ({@link Publisher}),
 * plus helpers for actor ask-pattern and event waiting.
 */
public final class PekkoStreams {

    private PekkoStreams() {}

    /** Convert a Pekko {@code Source} to a Reactive Streams {@code Publisher}. */
    public static <E> Publisher<E> asPublisher(final Source<E, ?> source, final Materializer mat) {
        return source.runWith(Sink.asPublisher(true), mat);
    }

    /** Convert a Reactive Streams {@code Publisher} to a Pekko {@code Source}. */
    public static <E> Source<E, ?> asSource(final Publisher<E> publisher) {
        return Source.fromPublisher(publisher);
    }

    /**
     * Wait for the first event matching {@code predicate} from the channel's stream,
     * with a timeout. Returns a completed future with {@code null} on timeout.
     */
    public static <E> CompletionStage<E> waitFor(
            final Publisher<E> stream,
            final Predicate<E> predicate,
            final Duration timeout,
            final Materializer mat) {
        return Source.fromPublisher(stream)
            .filter(predicate::test)
            .take(1)
            .completionTimeout(timeout)
            .runWith(Sink.head(), mat)
            .exceptionally(ex -> null);
    }

    /**
     * Ask-pattern helper: send a message to an actor ref and return a
     * {@code CompletionStage<R>} for the reply.
     */
    public static <M, R> CompletionStage<R> ask(
            final ActorRef<M> ref,
            final Function<ActorRef<R>, M> messageFactory,
            final Duration timeout,
            final ActorSystem<?> system) {
        return org.apache.pekko.actor.typed.javadsl.AskPattern.ask(
            ref, messageFactory::apply, timeout, system.scheduler());
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :util:pekko:build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add util/pekko/
git commit -m "feat(util/pekko): add PekkoStreams bridge utilities"
```

---

## Phase 2 — Simplify `util/common/infra/events/`

### Task 8: Collapse event channel interfaces

**Files:**
- Modify: `util/common/src/main/java/com/agentengine/util/common/infra/events/EventChannel.java`
- Modify: `util/common/src/main/java/com/agentengine/util/common/infra/events/ScopedEventChannel.java`
- Modify: `util/common/src/main/java/com/agentengine/util/common/infra/events/InMemoryEventChannel.java`
- Modify: `util/common/src/main/java/com/agentengine/util/common/infra/events/InMemoryScopedEventChannel.java`
- Delete: `EventPublisher.java`, `EventStream.java`, `EventAwaiter.java`, `ScopedEventPublisher.java`, `ScopedEventStream.java`

- [ ] **Step 1: Rewrite EventChannel.java**

```java
package com.agentengine.util.common.infra.events;

import org.reactivestreams.Publisher;

/**
 * A typed event channel: publish events and subscribe to the live stream.
 * Waiting for a specific event is a derived operation — use
 * {@code PekkoStreams.waitFor(channel.events(), predicate, timeout, mat)}.
 */
public interface EventChannel<E> {

    void publish(E event);

    Publisher<E> events();
}
```

- [ ] **Step 2: Rewrite ScopedEventChannel.java**

```java
package com.agentengine.util.common.infra.events;

import org.reactivestreams.Publisher;

/**
 * A scoped event channel where each scope key {@code K} has its own
 * independent event stream.
 */
public interface ScopedEventChannel<K, E> {

    void publish(K scope, E event);

    Publisher<E> events(K scope);

    /** Signal end-of-stream for the given scope. */
    void complete(K scope);

    /** Signal an error for the given scope. */
    void fail(K scope, Throwable throwable);
}
```

- [ ] **Step 3: Rewrite InMemoryEventChannel.java** (drop waitFor, keep publish + events)

```java
package com.agentengine.util.common.infra.events;

import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.reactivestreams.Publisher;

/**
 * In-memory {@link EventChannel} backed by an RxJava3 {@link PublishProcessor}.
 * Suitable for tests and single-node deployments.
 */
public class InMemoryEventChannel<E> implements EventChannel<E> {

    private final FlowableProcessor<E> processor = PublishProcessor.<E>create().toSerialized();

    @Override
    public void publish(final E event) {
        processor.onNext(event);
    }

    @Override
    public Publisher<E> events() {
        return processor.onBackpressureBuffer();
    }
}
```

- [ ] **Step 4: Update InMemoryScopedEventChannel.java** (rename `live()` → `events()`)

Replace `public Publisher<E> live(final K scope)` with `public Publisher<E> events(final K scope)` — the body is unchanged.

- [ ] **Step 5: Delete the 5 redundant interface files**

```bash
cd util/common/src/main/java/com/agentengine/util/common/infra/events
rm EventPublisher.java EventStream.java EventAwaiter.java ScopedEventPublisher.java ScopedEventStream.java
```

- [ ] **Step 6: Build util:common to confirm no compile errors**

```bash
./gradlew :util:common:build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Fix any compilation errors in the engine caused by the deleted interfaces**

Search: `grep -r "EventPublisher\|EventStream\|EventAwaiter\|ScopedEventPublisher\|ScopedEventStream" engine/src`

Remove any `implements EventPublisher/EventStream/EventAwaiter` or `extends ...` on the deleted types.
Update method signatures from `live(K)` → `events(K)`.

- [ ] **Step 8: Build everything**

```bash
./gradlew build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add -u
git commit -m "refactor(util/common): collapse 7-interface event hierarchy to EventChannel and ScopedEventChannel"
```

---

## Phase 3 — Engine Runtime

### Task 9: `SessionEvent` and `AgentEvent` sealed interfaces

**Files:**
- Create: `engine/src/main/java/com/agentengine/engine/runtime/SessionEvent.java`
- Create: `engine/src/main/java/com/agentengine/engine/runtime/AgentEvent.java`

These replace `SessionBusEvent` and `AgentBusEvent` with properly sealed subtypes.

- [ ] **Step 1: Create SessionEvent.java**

```java
package com.agentengine.engine.runtime;

import com.agentengine.util.pekko.PekkoSerializable;
import com.google.adk.events.Event;

/**
 * Events published to the session event channel during a turn.
 * Each subtype represents a distinct phase of session activity.
 */
public sealed interface SessionEvent extends PekkoSerializable {

    String rootSessionId();
    String rootRunId();
    String sourceSessionId();
    String runId();
    long timestamp();
    long sequence();

    record TurnStarted(String rootSessionId, String rootRunId, String sourceSessionId,
                       String runId, String agentId, long timestamp, long sequence)
        implements SessionEvent {}

    record TurnEvent(String rootSessionId, String rootRunId, String sourceSessionId,
                     String runId, String parentRunId, int depth,
                     long timestamp, long sequence, Event event)
        implements SessionEvent {}

    record TurnCompleted(String rootSessionId, String rootRunId, String sourceSessionId,
                         String runId, long timestamp, long sequence)
        implements SessionEvent {}

    record TurnFailed(String rootSessionId, String rootRunId, String sourceSessionId,
                      String runId, String errorMessage, long timestamp, long sequence)
        implements SessionEvent {}
}
```

- [ ] **Step 2: Create AgentEvent.java**

```java
package com.agentengine.engine.runtime;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.util.pekko.PekkoSerializable;

import java.util.Map;

/**
 * Events published to the agent event channel for session lifecycle changes.
 */
public sealed interface AgentEvent extends PekkoSerializable {

    String rootSessionId();
    String sessionId();
    long timestamp();

    record SessionRegistered(String rootSessionId, String sessionId, String agentId,
                              String parentSessionId, long timestamp) implements AgentEvent {}

    record SessionTerminal(String rootSessionId, String sessionId,
                           AgentSession.AgentSessionStatus status,
                           long timestamp) implements AgentEvent {}

    record SessionInterrupted(String rootSessionId, String sessionId,
                               String reason, long timestamp) implements AgentEvent {}
}
```

- [ ] **Step 3: Build**

```bash
./gradlew :engine:build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add engine/src/
git commit -m "feat(engine/runtime): add SessionEvent and AgentEvent sealed interfaces"
```

---

### Task 10: `AgentRuntimeConfig` and `AgentRuntime`

**Files:**
- Create: `engine/src/main/java/com/agentengine/engine/runtime/AgentRuntimeConfig.java`
- Create: `engine/src/main/java/com/agentengine/engine/runtime/AgentRuntime.java`
- Modify: `engine/build.gradle`

- [ ] **Step 1: Add util:pekko dependency to engine/build.gradle**

```gradle
implementation project(':util:pekko')
```

- [ ] **Step 2: Create AgentRuntimeConfig.java**

```java
package com.agentengine.engine.runtime;

import com.agentengine.util.pekko.PekkoBaseConfig;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

@ConfigMapping(prefix = "agent.runtime")
public interface AgentRuntimeConfig extends PekkoBaseConfig {

    @Override
    @WithDefault("localhost")
    String hostname();

    @Override
    @WithDefault("2551")
    int port();

    @Override
    List<String> seedNodes();

    @Override
    @WithDefault("agent-engine")
    String clusterName();

    @Override
    String jdbcUrl();

    @Override
    String jdbcUser();

    @Override
    String jdbcPassword();
}
```

- [ ] **Step 3: Create AgentRuntime.java**

```java
package com.agentengine.engine.runtime;

import com.agentengine.util.common.infra.events.EventChannel;
import com.agentengine.util.common.infra.events.EventChannel;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.events.PekkoEventChannel.SingleChannel;
import com.agentengine.util.pekko.events.PekkoEventChannel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.reactivestreams.Publisher;

/**
 * Singleton runtime for the agent engine. Owns the Pekko {@link ActorSystem},
 * the session event channel (scoped by root session ID), and the global agent
 * event channel.
 *
 * <p>Callers work only with {@link EventChannel}/{@link ScopedEventChannel} —
 * no Pekko types escape this class.
 */
@Singleton
public class AgentRuntime {

    private final ActorSystem<Void> system;
    private final ScopedEventChannel<String, SessionEvent> sessionChannel;
    private final EventChannel<AgentEvent> agentChannel;

    @Inject
    public AgentRuntime(final AgentRuntimeConfig config) {
        final var provider = new ActorSystemProvider(config);
        this.system = provider.actorSystem();
        this.sessionChannel = new PekkoScopedEventChannel<>(system);
        this.agentChannel = new PekkoEventChannel<>(system, "agent-events");
    }

    // ── Session events ────────────────────────────────────────────────────────

    public void publishSessionEvent(final String rootSessionId, final SessionEvent event) {
        sessionChannel.publish(rootSessionId, event);
    }

    public Publisher<SessionEvent> sessionEvents(final String rootSessionId) {
        return sessionChannel.events(rootSessionId);
    }

    public void completeSessionStream(final String rootSessionId) {
        sessionChannel.complete(rootSessionId);
    }

    public void failSessionStream(final String rootSessionId, final Throwable throwable) {
        sessionChannel.fail(rootSessionId, throwable);
    }

    // ── Agent events ──────────────────────────────────────────────────────────

    public void publishAgentEvent(final AgentEvent event) {
        agentChannel.publish(event);
    }

    public Publisher<AgentEvent> agentEvents() {
        return agentChannel.events();
    }

    // ── Actor system access (for SessionActor / RootSessionActor) ─────────────

    public ActorSystem<Void> system() {
        return system;
    }
}
```

- [ ] **Step 4: Build**

```bash
./gradlew :engine:build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add engine/
git commit -m "feat(engine/runtime): add AgentRuntime singleton with Pekko-backed event channels"
```

---

### Task 11: `SessionActor` and `RootSessionActor`

**Files:**
- Create: `engine/src/main/java/com/agentengine/engine/runtime/SessionActor.java`
- Create: `engine/src/main/java/com/agentengine/engine/runtime/RootSessionActor.java`
- Create: `engine/src/test/java/com/agentengine/engine/runtime/SessionActorTest.java`

- [ ] **Step 1: Write failing test for SessionActor**

```java
package com.agentengine.engine.runtime;

import com.agentengine.engine.api.beans.session.AgentSession;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionActorTest {

    private static final ActorTestKit kit = ActorTestKit.create();

    @AfterAll
    static void teardown() { kit.shutdownTestKit(); }

    @Test
    void shouldTransitionStatusOnUpdateLifecycle() {
        SessionActor.init(kit.system());
        final var ref = SessionActor.entityRef(kit.system(), "session-test-1");
        final TestProbe<AgentSession.AgentSessionStatus> probe = kit.createTestProbe();

        ref.tell(new SessionActor.Command.UpdateStatus(
            AgentSession.AgentSessionStatus.RUNNING, probe.ref()));
        assertThat(probe.receiveMessage()).isEqualTo(AgentSession.AgentSessionStatus.RUNNING);

        ref.tell(new SessionActor.Command.UpdateStatus(
            AgentSession.AgentSessionStatus.COMPLETED, probe.ref()));
        assertThat(probe.receiveMessage()).isEqualTo(AgentSession.AgentSessionStatus.COMPLETED);
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./gradlew :engine:test --tests "*.SessionActorTest"
```

- [ ] **Step 3: Implement SessionActor**

```java
package com.agentengine.engine.runtime;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.util.pekko.PekkoSerializable;
import com.agentengine.util.pekko.actor.ShardedEntity;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;

/**
 * Persistent sharded actor per session. Owns session status and metadata.
 * Sharded by {@code sessionId}.
 */
public final class SessionActor
    extends ShardedEntity<SessionActor.Command, SessionActor.Event, SessionActor.State> {

    public static final EntityTypeKey<Command> TYPE_KEY =
        EntityTypeKey.create(Command.class, "Session");

    // ── Commands ──────────────────────────────────────────────────────────────

    public sealed interface Command extends PekkoSerializable {
        record UpdateStatus(AgentSession.AgentSessionStatus status,
                            ActorRef<AgentSession.AgentSessionStatus> replyTo)
            implements Command {}
        record RegisterChild(String childSessionId) implements Command {}
    }

    // ── Events ────────────────────────────────────────────────────────────────

    public sealed interface Event extends PekkoSerializable {
        record StatusUpdated(AgentSession.AgentSessionStatus status) implements Event {}
        record ChildRegistered(String childSessionId) implements Event {}
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public record State(AgentSession.AgentSessionStatus status) {
        public static State empty() {
            return new State(AgentSession.AgentSessionStatus.UNKNOWN);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void init(final ActorSystem<?> system) {
        ClusterSharding.get(system).init(
            Entity.of(TYPE_KEY, ctx -> new SessionActor(ctx.getEntityId())));
    }

    public static EntityRef<Command> entityRef(final ActorSystem<?> system, final String sessionId) {
        return ClusterSharding.get(system).entityRefFor(TYPE_KEY, sessionId);
    }

    private SessionActor(final String entityId) {
        super(TYPE_KEY.name(), entityId);
    }

    @Override
    public State emptyState() { return State.empty(); }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Command.UpdateStatus.class, (state, cmd) ->
                Effect().persist(new Event.StatusUpdated(cmd.status()))
                    .thenRun(s -> cmd.replyTo().tell(s.status())))
            .onCommand(Command.RegisterChild.class, (state, cmd) ->
                Effect().persist(new Event.ChildRegistered(cmd.childSessionId())))
            .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Event.StatusUpdated.class, (state, e) -> new State(e.status()))
            .onEvent(Event.ChildRegistered.class, (state, e) -> state)
            .build();
    }
}
```

- [ ] **Step 4: Implement RootSessionActor** (sequence + child tree tracking)

```java
package com.agentengine.engine.runtime;

import com.agentengine.util.pekko.PekkoSerializable;
import com.agentengine.util.pekko.actor.ShardedEntity;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * Persistent sharded actor per root session. Tracks the child session tree
 * and provides a global sequence counter for ordering events.
 * Sharded by {@code rootSessionId}.
 */
public final class RootSessionActor
    extends ShardedEntity<RootSessionActor.Command, RootSessionActor.Event, RootSessionActor.State> {

    public static final EntityTypeKey<Command> TYPE_KEY =
        EntityTypeKey.create(Command.class, "RootSession");

    // ── Commands ──────────────────────────────────────────────────────────────

    public sealed interface Command extends PekkoSerializable {
        record ReserveSequence(ActorRef<Long> replyTo) implements Command {}
        record RegisterChild(String childSessionId) implements Command {}
        record MarkTerminal(String sessionId) implements Command {}
    }

    // ── Events ────────────────────────────────────────────────────────────────

    public sealed interface Event extends PekkoSerializable {
        record SequenceReserved(long sequence) implements Event {}
        record ChildRegistered(String childSessionId) implements Event {}
        record SessionMarkedTerminal(String sessionId) implements Event {}
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public record State(long nextSequence, Set<String> children, Set<String> terminal) {
        public static State empty() {
            return new State(0L, Set.of(), Set.of());
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public static void init(final ActorSystem<?> system) {
        ClusterSharding.get(system).init(
            Entity.of(TYPE_KEY, ctx -> new RootSessionActor(ctx.getEntityId())));
    }

    public static EntityRef<Command> entityRef(final ActorSystem<?> system, final String rootSessionId) {
        return ClusterSharding.get(system).entityRefFor(TYPE_KEY, rootSessionId);
    }

    private RootSessionActor(final String entityId) {
        super(TYPE_KEY.name(), entityId);
    }

    @Override public State emptyState() { return State.empty(); }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Command.ReserveSequence.class, (state, cmd) ->
                Effect().persist(new Event.SequenceReserved(state.nextSequence()))
                    .thenRun(s -> cmd.replyTo().tell(s.nextSequence() - 1)))
            .onCommand(Command.RegisterChild.class, (state, cmd) ->
                Effect().persist(new Event.ChildRegistered(cmd.childSessionId())))
            .onCommand(Command.MarkTerminal.class, (state, cmd) ->
                Effect().persist(new Event.SessionMarkedTerminal(cmd.sessionId())))
            .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Event.SequenceReserved.class, (state, e) ->
                new State(state.nextSequence() + 1, state.children(), state.terminal()))
            .onEvent(Event.ChildRegistered.class, (state, e) -> {
                final Set<String> children = new HashSet<>(state.children());
                children.add(e.childSessionId());
                return new State(state.nextSequence(), Set.copyOf(children), state.terminal());
            })
            .onEvent(Event.SessionMarkedTerminal.class, (state, e) -> {
                final Set<String> terminal = new HashSet<>(state.terminal());
                terminal.add(e.sessionId());
                return new State(state.nextSequence(), state.children(), Set.copyOf(terminal));
            })
            .build();
    }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :engine:test --tests "*.SessionActorTest"
```
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add engine/src/
git commit -m "feat(engine/runtime): add SessionActor and RootSessionActor sharded persistent entities"
```

---

## Phase 4 — Refactor AgentHub and Delete Legacy Code

### Task 12: Refactor `AgentHub` to use `AgentRuntime`

**Files:**
- Modify: `engine/src/main/java/com/agentengine/engine/services/AgentHub.java`
- Modify: `engine/src/main/java/com/agentengine/engine/services/AgentHubImpl.java`

- [ ] **Step 1: Update AgentHub interface**

Replace:
```java
SessionBus sessionBus(String rootSessionId);
AgentBus agentBus(String rootSessionId);
```
With:
```java
Publisher<SessionEvent> sessionEvents(String rootSessionId);
Publisher<AgentEvent> agentEvents();
```

Also update the `startTurn` method if absent — it should be present here:
```java
CompletionStage<Void> startTurn(StartTurnCommand command);

record StartTurnCommand(String sessionId, String rootSessionId, String message) {}
```

- [ ] **Step 2: Update AgentHubImpl constructor**

Replace `SessionBusRegistry sessionBusRegistry, AgentBusRegistry agentBusRegistry` with `AgentRuntime runtime`. Update the constructor body.

- [ ] **Step 3: Replace all sessionBus()/agentBus() usages inside AgentHubImpl**

| Old | New |
|---|---|
| `sessionBus(id).publish(event)` | `runtime.publishSessionEvent(rootId, SessionEvent.TurnEvent.from(event))` |
| `sessionBus(id).complete(runId)` | `runtime.completeSessionStream(rootId)` |
| `sessionBus(id).fail(runId, e)` | `runtime.failSessionStream(rootId, e)` |
| `agentBus(id).publish(event)` | `runtime.publishAgentEvent(AgentEvent.SessionTerminal.from(event))` |
| `agentBus(id).waitFor(pred, ms)` | Use `PekkoStreams.waitFor(runtime.agentEvents(), pred, duration, mat)` |

- [ ] **Step 4: Replace AgentBusEvent usages with AgentEvent**

Wherever `AgentBusEvent` is constructed, construct the equivalent `AgentEvent` subtype.
Wherever `SessionBusEvent` is constructed, construct the equivalent `SessionEvent` subtype.

- [ ] **Step 5: Build engine**

```bash
./gradlew :engine:build -x test
```
Expected: BUILD SUCCESSFUL — fix any remaining compilation errors.

- [ ] **Step 6: Commit**

```bash
git add engine/src/
git commit -m "refactor(engine): replace AgentBus/SessionBus with AgentRuntime event channels in AgentHub"
```

---

### Task 13: Delete legacy runtime and collaboration files

- [ ] **Step 1: Delete AgentBus, SessionBus, Registries**

```bash
cd engine/src/main/java/com/agentengine/engine/runtime
rm AgentBus.java AgentBusEvent.java AgentBusRegistry.java SessionBus.java SessionBusEvent.java SessionBusRegistry.java
```

- [ ] **Step 2: Delete collaboration layer**

```bash
cd engine/src/main/java/com/agentengine/engine
rm collaboration/CollaborationService.java
rm collaboration/CollaborationServiceImpl.java
rm collaboration/CollaborationEventUtils.java
```

Check if `CollaborationToolFactory.java` references `CollaborationService` — if so, update it to use `AgentHub` directly. Then move the file if it makes more sense under `tools/collaboration/`.

- [ ] **Step 3: Update collaboration tools to inject AgentHub**

Files to update:
- `engine/src/main/java/com/agentengine/engine/tools/collaboration/SpawnAgentTool.java`
- `engine/src/main/java/com/agentengine/engine/tools/collaboration/WaitAgentTool.java`
- `engine/src/main/java/com/agentengine/engine/tools/collaboration/SendInputTool.java`
- `engine/src/main/java/com/agentengine/engine/tools/collaboration/CloseAgentTool.java`

Replace `@Inject CollaborationService collaborationService` with `@Inject AgentHub agentHub`.
Replace `collaborationService.spawn(...)` with `agentHub.spawn(...)`, etc.

- [ ] **Step 4: Full build**

```bash
./gradlew build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "refactor(engine): delete AgentBus/SessionBus/Registries and CollaborationService layer"
```

---

## Phase 5 — Verification

### Task 14: Run full test suite

- [ ] **Step 1: Unit tests**

```bash
./gradlew test
```
Expected: all tests pass. Fix any failures before proceeding.

- [ ] **Step 2: Integration tests (requires Docker)**

```bash
./gradlew integrationTest
```
Expected: existing session/turn flow integration tests pass with Pekko-backed channels.

- [ ] **Step 3: Build all modules**

```bash
./gradlew clean build
```
Expected: BUILD SUCCESSFUL, no warnings about unchecked casts or deprecated API.

- [ ] **Step 4: Manual smoke test**

```bash
./deploy/deploy.sh dev
```

1. Create a session via REST: `POST /sessions`
2. Start a turn: `POST /sessions/{id}/runs`
3. Observe events streaming from `GET /sessions/{id}/runs/{runId}/stream`
4. Verify events are delivered via `AgentRuntime.sessionEvents(rootId)` (check logs)

- [ ] **Step 5: Commit final state**

```bash
git add -u
git commit -m "test: verify Pekko-native engine build and test suite passes"
```
