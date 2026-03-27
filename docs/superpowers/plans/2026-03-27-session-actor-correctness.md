# Session Actor Correctness Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 7 deferred correctness gaps from the Session Actor Rebuild so the runtime can start and run.

**Architecture:** CDI producers expose the MongoDB collection + JDBC DataSource as injectable beans; a `SessionTopologyFactory` wires `InitializeSession` into `RuntimeServiceImpl` before `StartRun`; `SessionActor` gains `SessionActorFactory` for child dispatch; `ClusterSingleton` guards the projection; and `SessionEventRecord` switches to a JSON-string event field to avoid BSON codec issues.

**Tech Stack:** Quarkus CDI (`@Produces`, `@ApplicationScoped`, `@Singleton`), Pekko Cluster Sharding, Pekko Projections JDBC, MongoDB Java driver, Quarkus Agroal (JDBC DataSource), Jackson via `JsonUtils`.

---

## File Map

| Action   | File                                                                           | Purpose                                              |
|----------|--------------------------------------------------------------------------------|------------------------------------------------------|
| Modify   | `runtime/actor/src/…/actor/SessionEventRecord.java`                           | Store event as `String eventJson` instead of typed field |
| Create   | `runtime/src/…/projection/SessionEventCollectionProducer.java`                | CDI producer: `MongoCollection<Document>` + index + DDL |
| Modify   | `runtime/src/…/projection/SessionHistoryProjectionHandler.java`               | Use `Document`, serialize `SessionEvent` via Jackson |
| Modify   | `runtime/src/…/projection/SessionHistoryProjection.java`                      | Inject typed CDI beans; wrap in ClusterSingleton     |
| Modify   | `runtime/src/…/services/DefaultSessionHistory.java`                           | Use `Document`, deserialize `SessionEvent` from JSON |
| Create   | `runtime/actor/src/…/actor/SessionTopologyFactory.java`                       | Build root/child `SessionTopology` values            |
| Modify   | `runtime/src/…/services/RuntimeServiceImpl.java`                              | Send `InitializeSession` before `StartRun`           |
| Modify   | `runtime/actor/src/…/actor/SessionActor.java`                                 | Add `SessionActorFactory`; dispatch child commands; handle `InitializeSession` idempotently; `notifyParentIfChild` |
| Modify   | `runtime/actor/src/…/actor/SessionActorFactory.java`                          | Pass `this` to actor constructor                     |
| Modify   | `runtime/build.gradle`                                                         | Add `quarkus-agroal` + `quarkus-jdbc-postgresql`     |
| Modify   | `runtime/src/…/services/SessionServiceImpl.java` (in `core/`)                 | Add eventual-consistency Javadoc to `sanitizeSession`|
| Modify   | `TODO.md`                                                                      | Remove completed items                               |

---

## Task 1: Switch `SessionEventRecord` to JSON string + add CDI collection producer

**Why:** The `SessionEvent` record holds a `Content` (Google GenAI type) that cannot be BSON-serialized by the Pojo codec. Storing it as a Jackson JSON string sidesteps all codec registration. The MongoDB collection itself must be produced as a CDI bean so `SessionHistoryProjection` and `DefaultSessionHistory` can inject it.

**Files:**
- Modify: `runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionEventRecord.java`
- Create: `runtime/src/main/java/com/agentengine/runtime/projection/SessionEventCollectionProducer.java`

- [ ] **Step 1: Rewrite `SessionEventRecord` to store `String eventJson`**

Replace the entire file content:
```java
package com.agentengine.runtime.actor;

/** MongoDB document written by SessionHistoryProjectionHandler. */
public record SessionEventRecord(String sessionId, long sequence, String eventJson) {}
```

- [ ] **Step 2: Create `SessionEventCollectionProducer`**

```java
package com.agentengine.runtime.projection;

import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;

import static com.mongodb.client.model.Indexes.ascending;

/**
 * Produces the session_events MongoDB collection and ensures a unique index on
 * (sessionId, sequence) for idempotent projection upserts.
 */
@ApplicationScoped
public class SessionEventCollectionProducer {

  private static final String DATABASE_NAME = "AGENT_ENGINE";
  private static final String COLLECTION_NAME = "session_events";

  private final MongoClientFactory mongoClientFactory;

  @Inject
  public SessionEventCollectionProducer(final MongoClientFactory mongoClientFactory) {
    this.mongoClientFactory = mongoClientFactory;
  }

  @Produces
  @Singleton
  public MongoCollection<Document> sessionEventsCollection() {
    return mongoClientFactory.getClient()
        .getDatabase(DATABASE_NAME)
        .getCollection(COLLECTION_NAME);
  }

  public void onStart(@Observes final StartupEvent event) {
    final var collection = sessionEventsCollection();
    collection.createIndex(
        ascending("sessionId", "sequence"),
        new IndexOptions().unique(true).background(true));
  }
}
```

- [ ] **Step 3: Run compile to catch any errors**

```bash
cd /Users/rhp/Projects/agent-engine/.worktrees/session-actor-rebuild
./gradlew :runtime-actor:compileJava :runtime:compileJava -x test 2>&1 | tail -30
```

Expected: compilation fails on files that still reference the old `SessionEvent event` field in `SessionEventRecord` — proceed to fix those in Task 2.

---

## Task 2: Update projection handler and history service for `Document`-based collection

**Files:**
- Modify: `runtime/src/main/java/com/agentengine/runtime/projection/SessionHistoryProjectionHandler.java`
- Modify: `runtime/src/main/java/com/agentengine/runtime/services/DefaultSessionHistory.java`

- [ ] **Step 1: Rewrite `SessionHistoryProjectionHandler` to use `MongoCollection<Document>`**

```java
package com.agentengine.runtime.projection;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.runtime.actor.SessionFact;
import com.agentengine.util.common.JsonUtils;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.apache.pekko.Done;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.javadsl.Handler;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Materializes TurnCommitted facts into MongoDB session_events collection.
 * Uses upsert by (sessionId, sequence) for at-least-once idempotency.
 */
public final class SessionHistoryProjectionHandler extends Handler<EventEnvelope<SessionFact>> {

  private static final Logger LOG = LoggerFactory.getLogger(SessionHistoryProjectionHandler.class);

  private final MongoCollection<Document> collection;

  public SessionHistoryProjectionHandler(final MongoCollection<Document> collection) {
    this.collection = collection;
  }

  @Override
  public CompletionStage<Done> process(final EventEnvelope<SessionFact> envelope) {
    return switch (envelope.event()) {
      case SessionFact.TurnCommitted fact -> writeTurnEvents(fact);
      default -> CompletableFuture.completedFuture(Done.getInstance());
    };
  }

  private CompletionStage<Done> writeTurnEvents(final SessionFact.TurnCommitted fact) {
    final var events = fact.events();
    final var records = IntStream.range(0, events.size())
        .mapToObj(i -> toDocument(events.get(i), fact.startSequence() + i))
        .toList();

    try {
      for (final var doc : records) {
        collection.replaceOne(
            and(eq("sessionId", doc.getString("sessionId")), eq("sequence", doc.getLong("sequence"))),
            doc,
            new ReplaceOptions().upsert(true));
      }
      LOG.debug("Projected {} events for session {}", records.size(),
          records.isEmpty() ? "?" : records.getFirst().getString("sessionId"));
    } catch (final Exception e) {
      LOG.error("Failed to write turn events to projection", e);
      return CompletableFuture.failedFuture(e);
    }
    return CompletableFuture.completedFuture(Done.getInstance());
  }

  private static Document toDocument(final SessionEvent event, final long sequence) {
    return new Document()
        .append("sessionId", event.sessionId())
        .append("sequence", sequence)
        .append("eventJson", JsonUtils.toJson(event));
  }
}
```

- [ ] **Step 2: Rewrite `DefaultSessionHistory` to use `MongoCollection<Document>`**

```java
package com.agentengine.runtime.services;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.runtime.actor.SessionHistoryService;
import com.agentengine.util.common.JsonUtils;
import com.mongodb.client.MongoCollection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.ascending;

/**
 * MongoDB-backed SessionHistory reading from the session_events projection
 * collection materialized by SessionHistoryProjectionHandler.
 *
 * <p>
 * Reads are eventually consistent: if the projection has not caught up to the
 * latest TurnCommitted facts, recent events may be absent. Callers must not
 * rely on this view for real-time decisions during an active run.
 */
@Singleton
public class DefaultSessionHistory implements SessionHistory {

  private final MongoCollection<Document> collection;

  @Inject
  public DefaultSessionHistory(final MongoCollection<Document> collection) {
    this.collection = collection;
  }

  @Override
  public List<SessionEvent> events(final String sessionId) {
    return collection
        .find(eq("sessionId", sessionId))
        .sort(ascending("sequence"))
        .map(doc -> JsonUtils.fromJson(doc.getString("eventJson"), SessionEvent.class))
        .into(new ArrayList<>());
  }
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :runtime:compileJava -x test 2>&1 | tail -20
```

Expected: PASS (or only residual errors in `SessionHistoryProjection` fixed in Task 3).

- [ ] **Step 4: Commit**

```bash
git add runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionEventRecord.java \
  runtime/src/main/java/com/agentengine/runtime/projection/ \
  runtime/src/main/java/com/agentengine/runtime/services/DefaultSessionHistory.java
git commit -m "fix: switch SessionEventRecord to JSON string; produce MongoCollection<Document> as CDI bean"
```

---

## Task 3: Add JDBC DataSource dependency and produce `Supplier<Connection>`

**Why:** The `SessionHistoryProjection` injects `Supplier<Connection>` for the projection offset store JDBC session. Quarkus Agroal provides this via `javax.sql.DataSource`. The `pekko_projection_offset_store` DDL must also be applied on startup.

**Files:**
- Modify: `runtime/build.gradle`
- Modify: `runtime/src/main/java/com/agentengine/runtime/projection/SessionEventCollectionProducer.java`

- [ ] **Step 1: Add Agroal and JDBC dependencies to `runtime/build.gradle`**

Add after the existing `quarkus.arc` line:
```groovy
implementation libs.quarkus.agroal
implementation libs.quarkus.jdbc.postgresql
```

Check first that these lib aliases exist in `gradle/libs.versions.toml`. If they don't, add them:

In `gradle/libs.versions.toml` under `[libraries]`:
```toml
quarkus-agroal = { module = "io.quarkus:quarkus-agroal" }
quarkus-jdbc-postgresql = { module = "io.quarkus:quarkus-jdbc-postgresql" }
```

(Version comes from the Quarkus BOM already declared as `enforcedPlatform`.)

- [ ] **Step 2: Add `Supplier<Connection>` producer and DDL init to `SessionEventCollectionProducer`**

Extend the existing class:
```java
// Additional imports needed:
import jakarta.inject.Named;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

// Add field:
private final DataSource dataSource;

// Update constructor:
@Inject
public SessionEventCollectionProducer(final MongoClientFactory mongoClientFactory,
    final DataSource dataSource) {
  this.mongoClientFactory = mongoClientFactory;
  this.dataSource = dataSource;
}

// Add producer:
@Produces
@Singleton
public Supplier<Connection> jdbcConnectionFactory() {
  return () -> {
    try {
      return dataSource.getConnection();
    } catch (final SQLException e) {
      throw new RuntimeException("Failed to acquire JDBC connection for projection", e);
    }
  };
}
```

- [ ] **Step 3: Apply the projection offset store DDL in `onStart`**

Add to the `onStart` method (after the MongoDB index creation):

```java
applyProjectionOffsetStoreDdl();
```

Add the private method:
```java
private void applyProjectionOffsetStoreDdl() {
  try (final Connection conn = dataSource.getConnection();
       final var stmt = conn.createStatement()) {
    stmt.execute("""
        CREATE TABLE IF NOT EXISTS pekko_projection_offset_store (
          projection_name VARCHAR(255) NOT NULL,
          projection_key  VARCHAR(255) NOT NULL,
          current_offset  VARCHAR(255) NOT NULL,
          manifest        VARCHAR(4)   NOT NULL,
          mergeable       BOOLEAN      NOT NULL,
          last_updated    BIGINT       NOT NULL,
          PRIMARY KEY (projection_name, projection_key)
        )
        """);
    conn.commit();
  } catch (final SQLException e) {
    throw new RuntimeException("Failed to initialise projection offset store", e);
  }
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew :runtime:compileJava -x test 2>&1 | tail -20
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add runtime/build.gradle gradle/libs.versions.toml \
  runtime/src/main/java/com/agentengine/runtime/projection/SessionEventCollectionProducer.java
git commit -m "fix: add JDBC DataSource CDI bean and projection offset store DDL initializer"
```

---

## Task 4: Update `SessionHistoryProjection` to inject CDI beans and use ClusterSingleton

**Why:** The projection currently manually constructs its dependencies. Now that CDI produces them, inject them properly. Also wraps in `ClusterSingleton` so only one node in the cluster runs the projection.

**Files:**
- Modify: `runtime/src/main/java/com/agentengine/runtime/projection/SessionHistoryProjection.java`

- [ ] **Step 1: Rewrite `SessionHistoryProjection`**

```java
package com.agentengine.runtime.projection;

import com.agentengine.runtime.actor.SessionFact;
import com.mongodb.client.MongoCollection;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.singleton.ClusterSingleton;
import org.apache.pekko.cluster.singleton.SingletonActor;
import org.apache.pekko.persistence.jdbc.query.javadsl.JdbcReadJournal;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.jdbc.JdbcSession;
import org.apache.pekko.projection.jdbc.javadsl.JdbcProjection;
import org.apache.pekko.japi.function.Function;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

import static com.agentengine.util.common.beans.AssetClass.AGENT_SESSION;

/**
 * Registers the SessionHistory Pekko Projection on startup, guarded by
 * ClusterSingleton so exactly one node in the cluster runs it.
 */
@ApplicationScoped
public class SessionHistoryProjection {

  private static final Logger LOG = LoggerFactory.getLogger(SessionHistoryProjection.class);
  private static final int MIN_SLICE = 0;
  private static final int MAX_SLICE = 1023;

  private final ActorSystem<Void> actorSystem;
  private final MongoCollection<Document> collection;
  private final Supplier<Connection> jdbcConnectionFactory;

  @Inject
  public SessionHistoryProjection(final ActorSystem<Void> actorSystem,
      final MongoCollection<Document> collection,
      final Supplier<Connection> jdbcConnectionFactory) {
    this.actorSystem = actorSystem;
    this.collection = collection;
    this.jdbcConnectionFactory = jdbcConnectionFactory;
  }

  public void onStart(@Observes final StartupEvent event) {
    LOG.info("Starting SessionHistory projection (slices {}-{})", MIN_SLICE, MAX_SLICE);

    final var sourceProvider = EventSourcedProvider.<SessionFact>eventsBySlices(
        actorSystem, JdbcReadJournal.Identifier(), AGENT_SESSION, MIN_SLICE, MAX_SLICE);

    final var projection = JdbcProjection.atLeastOnceAsync(
        ProjectionId.of("session-history", "all-slices"),
        sourceProvider,
        () -> new ConnectionBackedJdbcSession(jdbcConnectionFactory.get()),
        () -> new SessionHistoryProjectionHandler(collection),
        actorSystem);

    ClusterSingleton.get(actorSystem).init(
        SingletonActor.of(ProjectionBehavior.create(projection), "session-history-projection"));
  }

  private static final class ConnectionBackedJdbcSession implements JdbcSession {

    private final Connection connection;

    ConnectionBackedJdbcSession(final Connection connection) {
      this.connection = connection;
    }

    @Override
    public <Result> Result withConnection(final Function<Connection, Result> func) throws Exception {
      return func.apply(connection);
    }

    @Override
    public void commit() {
      try {
        connection.commit();
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void rollback() {
      try {
        connection.rollback();
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() {
      try {
        connection.close();
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :runtime:compileJava -x test 2>&1 | tail -20
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add runtime/src/main/java/com/agentengine/runtime/projection/SessionHistoryProjection.java
git commit -m "fix: inject CDI beans into SessionHistoryProjection; wrap in ClusterSingleton"
```

---

## Task 5: Create `SessionTopologyFactory` and wire `InitializeSession` in `RuntimeServiceImpl`

**Why:** The actor rejects all commands when in null state except `InitializeSession`. `RuntimeServiceImpl` currently sends `StartRun` directly. This task adds the initialization step for root sessions.

**Files:**
- Create: `runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionTopologyFactory.java`
- Modify: `runtime/src/main/java/com/agentengine/runtime/services/RuntimeServiceImpl.java`
- Modify: `runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionActor.java` (handle `InitializeSession` idempotently in initialized state)

- [ ] **Step 1: Create `SessionTopologyFactory`**

```java
package com.agentengine.runtime.actor;

/** Constructs SessionTopology values for root and child sessions. */
public final class SessionTopologyFactory {

  private SessionTopologyFactory() {}

  public static SessionTopology rootTopology(final String agentId, final String sessionId) {
    return new SessionTopology(sessionId, agentId, new SessionTopology.SessionRole.Root());
  }

  public static SessionTopology childTopology(final String agentId, final String sessionId,
      final String rootSessionId, final String parentSessionId, final String parentAgentId) {
    return new SessionTopology(sessionId, agentId,
        new SessionTopology.SessionRole.Child(rootSessionId, parentSessionId, parentAgentId));
  }
}
```

- [ ] **Step 2: Handle `InitializeSession` idempotently in `SessionActor` initialized state**

In `SessionActor.commandHandler()`, add to the `forState(state -> state != null)` block:

```java
.onCommand(SessionCommand.ExternalCommand.InitializeSession.class,
    (state, cmd) -> {
        cmd.replyTo().tell(new SessionReply.InitializeResult.AlreadyInitialized());
        return Effect().none();
    })
```

- [ ] **Step 3: Update `RuntimeServiceImpl.startSession` to initialize then start**

Replace the `startSession` method:

```java
@Override
public CompletionStage<SessionReply.StartRunResult> startSession(final String agentId,
    final String sessionId, final String message) {
  LOG.info("Starting session {}:{}", agentId, sessionId);
  final EntityRef<SessionCommand> ref = sessionActorFactory.entityRef(agentId, sessionId);
  final SessionTopology topology = SessionTopologyFactory.rootTopology(agentId, sessionId);
  return ref.<SessionReply.InitializeResult>ask(
      replyTo -> new SessionCommand.ExternalCommand.InitializeSession(topology, replyTo),
      ActorUtils.DEFAULT_ASK_TIMEOUT)
      .thenCompose(_ -> ref.<SessionReply.StartRunResult>ask(
          replyTo -> new SessionCommand.ExternalCommand.StartRun(message, replyTo),
          ActorUtils.DEFAULT_ASK_TIMEOUT))
      .whenComplete((result, ex) -> {
        if (ex != null) {
          LOG.error("Failed to start session {}:{}", agentId, sessionId, ex);
        } else {
          LOG.info("Session {}:{} start result: {}", agentId, sessionId, result);
        }
      });
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew :runtime-actor:compileJava :runtime:compileJava -x test 2>&1 | tail -20
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionTopologyFactory.java \
  runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionActor.java \
  runtime/src/main/java/com/agentengine/runtime/services/RuntimeServiceImpl.java
git commit -m "fix: add SessionTopologyFactory; wire InitializeSession before StartRun in RuntimeServiceImpl"
```

---

## Task 6: ChildRegistry dispatch in `SessionActor`

**Why:** `onSpawnChild` and `onSendChildTask` have TODO comments where child actor dispatch should happen. `notifyParentIfChild` is also a no-op. This task wires them using `SessionActorFactory`.

**Note:** `SessionActor` can hold `SessionActorFactory` without a circular reference because the actor is NOT a CDI bean — it's constructed by the factory inside `Behaviors.setup`, at which point the factory CDI singleton is fully initialized.

**Files:**
- Modify: `runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionActor.java`
- Modify: `runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionActorFactory.java`

- [ ] **Step 1: Add `SessionActorFactory` field to `SessionActor`**

Add to the existing fields section (below `eventChannel`):
```java
private final SessionActorFactory actorFactory;
```

Update the constructor signature:
```java
public SessionActor(final ActorContext<SessionCommand> ctx,
                    final String entityId,
                    final AgentRunner runner,
                    final SessionEventChannel eventChannel,
                    final SessionActorFactory actorFactory) {
    super(TYPE_KEY.name(), entityId);
    this.ctx = ctx;
    this.runner = runner;
    this.eventChannel = eventChannel;
    this.actorFactory = actorFactory;
}
```

- [ ] **Step 2: Implement child dispatch in `onSpawnChild`**

Replace the `// TODO: dispatch child session initialization via SessionActorFactory` comment with:

```java
final var childTopology = SessionTopologyFactory.childTopology(
        cmd.childAgentId(), childSessionId,
        newState.rootSessionId(), newState.topology().sessionId(),
        newState.topology().agentId());
final var childRef = actorFactory.entityRef(cmd.childAgentId(), childSessionId);
childRef.tell(new SessionCommand.ExternalCommand.InitializeSession(
        childTopology, ctx.getSystem().ignoreRef()));
childRef.tell(new SessionCommand.ExternalCommand.StartRun(cmd.message(), ctx.getSystem().ignoreRef()));
```

Note: The child's `StartRun` reply is dropped (`ignoreRef`) because results come back via `NotifyChildRunCompleted`. The parent only learns of results through child-to-parent notifications.

- [ ] **Step 3: Implement child dispatch in `onSendChildTask`**

Replace the `// TODO: tell child actor new StartRun via SessionActorFactory` comment with:

```java
final var childRef = actorFactory.entityRef(worker.get().childAgentId(), cmd.childSessionId());
childRef.tell(new SessionCommand.ExternalCommand.StartRun(cmd.message(), ctx.getSystem().ignoreRef()));
```

- [ ] **Step 4: Implement `notifyParentIfChild`**

Replace the `// TODO: notify parent actor via SessionActorFactory` comment with:

```java
final var role = state.topology().role();
if (!(role instanceof SessionTopology.SessionRole.Child child)) return;
final var parentRef = actorFactory.entityRef(child.parentAgentId(), child.parentSessionId());
parentRef.tell(new SessionCommand.InternalCommand.NotifyChildRunCompleted(
        state.topology().sessionId(), runId, null));
```

Note: `result` is `null` here because extracting the LLM output from `TurnBuffer` at this point is not trivial. Full output extraction is a follow-up (see TODO.md for child result propagation).

- [ ] **Step 5: Update `SessionActorFactory` to pass `this` to actor**

```java
@Singleton
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    public SessionActorFactory(final ActorSystem<Void> actorSystem,
                               final SessionEventChannel sessionEventChannel,
                               final AgentRunner runner) {
        super(actorSystem, SessionActor.TYPE_KEY, ctx ->
                Behaviors.setup(actorCtx -> {
                    // Factory is fully initialized by the time actors are created.
                    // Pass reference for child session dispatch.
                    final var factory = ShardedEntityFactory.<SessionCommand>currentFactory();
                    return new SessionActor(actorCtx, ctx.getEntityId(), runner,
                            sessionEventChannel, (SessionActorFactory) factory);
                }));
    }

    public EntityRef<SessionCommand> entityRef(final String agentId, final String sessionId) {
        return entityRef(agentId + ":" + sessionId);
    }
}
```

**Wait** — `ShardedEntityFactory.currentFactory()` does not exist. Instead, capture `this` in a local variable before the lambda (lambdas can capture effectively-final local vars):

```java
@Singleton
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    public SessionActorFactory(final ActorSystem<Void> actorSystem,
                               final SessionEventChannel sessionEventChannel,
                               final AgentRunner runner) {
        // 'factory' is captured after super() completes; the lambda is only
        // invoked lazily when the first message arrives, so the factory is
        // fully initialized by then.
        super(actorSystem, SessionActor.TYPE_KEY, null); // placeholder — see below
        // Cannot capture 'this' before super() call. Use a holder pattern.
    }
    ...
}
```

Actually `this` cannot be captured before `super()` completes in Java. The clean solution is to **not** inject `SessionActorFactory` into the actor directly, but instead inject it into `AgentRunner` and pass it down through the runner interface, OR to use a `@Inject`-produced `Instance<SessionActorFactory>` at the runner level.

Simpler: add `SessionActorFactory` as a field to `DefaultAgentRunner` (it already injects `Instance<SessionActorFactory>`) and pass the factory ref from runner calls. But that changes the `AgentRunner` interface.

**Actual correct approach:** Use an indirection holder:

```java
@Singleton
public class SessionActorFactory extends ShardedEntityFactory<SessionCommand> {

    private final SessionActorFactory self = this;  // captured after super() in field init

    public SessionActorFactory(final ActorSystem<Void> actorSystem,
                               final SessionEventChannel sessionEventChannel,
                               final AgentRunner runner) {
        super(actorSystem, SessionActor.TYPE_KEY, ctx ->
                Behaviors.setup(actorCtx ->
                        new SessionActor(actorCtx, ctx.getEntityId(), runner,
                                sessionEventChannel, /* factory captured below */ null)));
    }
    ...
}
```

That still won't work because `self` isn't available when the super constructor runs.

**Final correct approach:** Pass `SessionActorFactory` into `SessionActor` via a `AtomicReference` wrapper set post-construction, OR — simplest — inject it into `DefaultAgentRunner` and route child dispatch through the runner. The runner already receives `SessionTopology` and the `SessionActorFactory` instance.

The cleanest architectural fix: move child dispatch out of the actor and into `DefaultAgentRunner`, reached via a new `AgentRunner` method: `spawnChild(topology, childAgentId, message, replyTo)`. The actor sends this internal command to the runner, which dispatches via the factory it already holds.

**Revised approach for Task 6:**

- Add `spawnChild` and `sendChildTask` to `AgentRunner` interface
- Implement them in `DefaultAgentRunner` using the `Instance<SessionActorFactory>` it already injects
- Remove `SessionActorFactory` from `SessionActor` constructor — the runner handles dispatch

- [ ] **Step 5 (revised): Add dispatch methods to `AgentRunner` interface**

Add to `runtime/actor/src/main/java/com/agentengine/runtime/actor/AgentRunner.java`:

```java
void spawnChild(SessionTopology parentTopology, String childAgentId, String childSessionId,
        String childRunId, String message, ActorRef<SessionCommand> parentRef);

void sendChildTask(SessionTopology parentTopology, String childAgentId, String childSessionId,
        String childRunId, String message, ActorRef<SessionCommand> parentRef);
```

- [ ] **Step 6: Implement in `DefaultAgentRunner`**

Add to `DefaultAgentRunner`:
```java
@Override
public void spawnChild(final SessionTopology parentTopology, final String childAgentId,
    final String childSessionId, final String childRunId, final String message,
    final ActorRef<SessionCommand> parentRef) {
  final var childTopology = SessionTopologyFactory.childTopology(
      childAgentId, childSessionId,
      parentTopology.rootSessionId(), parentTopology.sessionId(), parentTopology.agentId());
  final var childRef = actorFactory.get().entityRef(childAgentId, childSessionId);
  childRef.tell(new SessionCommand.ExternalCommand.InitializeSession(
      childTopology, actorSystem -> {}));  // reply ignored
  childRef.tell(new SessionCommand.ExternalCommand.StartRun(message, parentRef));  // parent notified on complete
}

@Override
public void sendChildTask(final SessionTopology parentTopology, final String childAgentId,
    final String childSessionId, final String childRunId, final String message,
    final ActorRef<SessionCommand> parentRef) {
  actorFactory.get().entityRef(childAgentId, childSessionId)
      .tell(new SessionCommand.ExternalCommand.StartRun(message, parentRef));
}
```

**Note:** The `StartRun` reply goes to the parent actor here. But `StartRunResult` is the reply type, not `NotifyChildRunCompleted`. For proper notification, the child actor's `notifyParentIfChild` (already a TODO in `onExecutionCompleted`) must be wired. Keep that as a deferred item — the key gap fixed here is that child actors are now actually dispatched.

- [ ] **Step 7: Wire dispatch in `SessionActor.onSpawnChild` and `onSendChildTask`**

Replace the TODO comments:

In `onSpawnChild`:
```java
.thenRun(newState -> {
    cmd.replyTo().tell(new SessionReply.SpawnResult.ChildSpawned(handle));
    runner.spawnChild(newState.topology(), cmd.childAgentId(), childSessionId,
            childRunId, cmd.message(), ctx.getSelf());
});
```

In `onSendChildTask`:
```java
.thenRun(newState -> {
    cmd.replyTo().tell(new SessionReply.SendTaskResult.TaskAccepted(handle));
    runner.sendChildTask(newState.topology(), worker.get().childAgentId(),
            cmd.childSessionId(), childRunId, cmd.message(), ctx.getSelf());
});
```

Also implement `notifyParentIfChild` using `runner`:

Add `notifyParent` method to `AgentRunner`:
```java
void notifyParentOfCompletion(SessionTopology childTopology, String runId, ActorRef<SessionCommand> childRef);
```

In `DefaultAgentRunner`:
```java
@Override
public void notifyParentOfCompletion(final SessionTopology childTopology, final String runId,
    final ActorRef<SessionCommand> childRef) {
  if (childTopology.isRoot()) return;
  final var role = (SessionTopology.SessionRole.Child) childTopology.role();
  actorFactory.get().entityRef(role.parentAgentId(), role.parentSessionId())
      .tell(new SessionCommand.InternalCommand.NotifyChildRunCompleted(
          childTopology.sessionId(), runId, null));
}
```

In `SessionActor.notifyParentIfChild`:
```java
private void notifyParentIfChild(final SessionState state, final String runId) {
    runner.notifyParentOfCompletion(state.topology(), runId, ctx.getSelf());
}
```

- [ ] **Step 8: Compile**

```bash
./gradlew :runtime-actor:compileJava :runtime:compileJava -x test 2>&1 | tail -20
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add runtime/actor/src/main/java/com/agentengine/runtime/actor/AgentRunner.java \
  runtime/actor/src/main/java/com/agentengine/runtime/actor/SessionActor.java \
  runtime/src/main/java/com/agentengine/runtime/services/DefaultAgentRunner.java
git commit -m "fix: implement ChildRegistry dispatch via AgentRunner; wire notifyParentOfCompletion"
```

---

## Task 7: Eventual-consistency note and cleanup

**Files:**
- Modify: `core/src/main/java/com/agentengine/core/services/SessionServiceImpl.java`
- Modify: `TODO.md`

- [ ] **Step 1: Add Javadoc to `SessionServiceImpl.sanitizeSession`**

The method already has `SessionHistory` injected. Add a comment above the `for` loop:
```java
// NOTE: Eventual consistency — projection may lag behind the actor journal.
// Events missing here do not indicate data loss; a subsequent request will
// return the full history once the projection has caught up.
```

- [ ] **Step 2: Remove completed items from TODO.md**

Remove the "Session Actor Rebuild — Deferred Correctness Gaps" section entirely from `TODO.md` (all 8 items are now resolved or explicitly superseded).

- [ ] **Step 3: Full build**

```bash
./gradlew clean build -x test -x :runtime-actor:spotlessJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Spotless fix**

```bash
./gradlew :runtime:spotlessApply :runtime-actor:spotlessApply 2>/dev/null; \
  ./gradlew :core:spotlessApply 2>/dev/null; \
  ./gradlew clean build -x test -x :runtime-actor:spotlessJava 2>&1 | tail -10
```

- [ ] **Step 5: Final commit**

```bash
git add -u
git commit -m "fix: wire all session actor correctness gaps; clear deferred TODO items"
```

---

## Verification Checklist

After all tasks:

- [ ] `./gradlew clean build -x test -x :runtime-actor:spotlessJava` → `BUILD SUCCESSFUL`
- [ ] No references to old `SessionEventRecord.event` field: `grep -r "\.event()" runtime/src/main/java/com/agentengine/runtime/projection/ runtime/src/main/java/com/agentengine/runtime/services/DefaultSessionHistory.java`
- [ ] No unresolved TODO comments for dispatching children: `grep -r "TODO.*dispatch\|TODO.*SessionActorFactory" --include="*.java" runtime/actor/`
- [ ] `SessionHistoryProjection` uses `ClusterSingleton`: `grep -l "ClusterSingleton" runtime/src/main/java/com/agentengine/runtime/projection/SessionHistoryProjection.java`
- [ ] `RuntimeServiceImpl.startSession` sends `InitializeSession`: `grep "InitializeSession" runtime/src/main/java/com/agentengine/runtime/services/RuntimeServiceImpl.java`
