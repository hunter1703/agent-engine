# 2. Runtime Architecture

## 2.1 Core Services

Configuration and session CRUD live in the **Catalog** service (`../catalog/core/src/com/agentengine/catalog/core/services`):

- `AgentServiceImpl`: CRUD/query for agent configs
- `ModelServiceImpl`: CRUD/query for model configs
- `SessionServiceImpl`: session CRUD, title updates, and deletion event publishing

Execution lives in the **Agent** service (`agent/core/src/main/java/com/agentengine/agent/core/services`):

- `RuntimeServiceImpl`: the execution center — starts sessions, streams events, handles confirmations and rollback
- `SessionHistoryServiceImpl`: reconstructs committed session history from actor state

## 2.2 Execution Lifecycle (`RuntimeServiceImpl`)

Execution is built on **event-sourced, cluster-sharded session actors** (Apache Pekko). Each session is a `SessionActor`, a `ShardedEntity<SessionCommand, SessionFact, SessionActorState>` — commands drive behavior, facts are the persisted event journal, and state is rebuilt by replaying facts.

For a request, `RuntimeServiceImpl`:

1. Resolves the target session (existing or new) and effective agent config.
2. Obtains the session's actor reference via `SessionActorFactory` (cluster sharding routes by session ID, so a given session is owned by exactly one actor across the cluster).
3. Issues a command to the actor (`ask` with `SessionActorFactory.ASK_TIMEOUT`) — `startSession`, `confirmSession`, `rollbackSession`, or a subscribe.
4. The actor builds a `SessionRunner` via `RunnerFactory`, which constructs the ADK `Runner` over the resolved agent graph, model, toolsets, and resumability config.
5. Execution runs with `RunConfig` in SSE streaming mode; events are returned as a `Publisher<SessionEvent>`.
6. The actor persists finalized events to its journal and updates the session title on completion.

Because sessions are sharded entities, lifecycle (activation, passivation, recovery) is managed by Pekko cluster sharding rather than an in-process cache.

## 2.3 Agent Construction

`AgentProvider` selects an `AgentFactory` by agent type:

- `default` → standard LLM agent (`DelegatedAgent` wrapping an ADK `LlmAgent`)
- `orchestrator` → `OrchestratorAgentFactory`

Orchestrator branches:

- `TRANSFER`: handoff-capable manager agent with native transfer targets
- `SEQUENTIAL`: ADK `SequentialAgent`
- `PARALLEL`: custom `ParallelOrchestratorAgent`

## 2.4 Parallel Orchestration Semantics

`ParallelOrchestratorAgent` executes sub-agents concurrently and emits only the aggregated orchestrator output.

Stopping policies:

- `ALL_COMPLETE`
- `FIRST_SUCCESS`
- `QUORUM`

Aggregation policies:

- `CONCATENATE`
- `BEST_EFFORT`
- `MAJORITY_VOTE`

If the policy target cannot be satisfied, the runtime falls back to deterministic best-effort output and records a `parallel_policy_fallback` violation in run state.

## 2.5 Model Provisioning

`ModelProvider` resolves model config from the model repository, picks a `ModelFactory` by provider (`ModelConfig.Provider`), and caches instantiated model clients in a `RefCountedCache`.

Cache behavior:

- Name: `model-provider`
- Idle timeout: 15 minutes
- Cleanup interval: 60 seconds
- Auto-close on eviction if the model implements `AutoCloseable`

## 2.6 Cross-Cutting Runtime Components

Execution wires guardrails and context management directly into the model flow (`BaseFlow`).

Request processors:
- `CorrectionProcessor`
- `ReminderRequestProcessor`

Response processors:
- `ToolCallSanitizationResponseProcessor`
- `PlanLoopResponseProcessor`

Terminal step/run semantics follow ADK event semantics directly:
- terminal event = `Event.finalResponse()` or `EventActions.endInvocation()`
- the engine does not synthesize `turnComplete` or reorder response parts after the model

### Context management responsibilities

- context manager prompt rebuild before model invocation

### Guardrail responsibilities

- input guardrails before model invocation
- tool guardrails before tool call execution
- output guardrails after model generation
- optimistic mode support with async output guardrail futures
- native escalation behavior for tool confirmations and synthetic internal human-input tool calls

## 2.7 Context Management

Context manager selection is strategy-driven:

- `compaction`
- `last_n`
- `none`

The context manager is applied in `ContextManagementPlugin.beforeModelCallback`, replacing request content with a rebuilt prompt sequence.

## 2.8 Session Persistence

Session state is **event-sourced through the `SessionActor`** rather than a separate repository. The actor appends `SessionFact` entries (its event journal) and rebuilds `SessionActorState` by replaying them on recovery.

- only finalized (non-partial) events are appended
- `SessionHistoryServiceImpl` reconstructs committed history from the actor's replay state
- history can be filtered by recent-event count or timestamp
- the ADK session view is derived from this committed history

## 2.9 Persistent Memory Service

`MemoryService` implements the ADK `BaseMemoryService` and provides cross-session memory scoped per agent and user, backed by Qdrant.

### Extraction flow

After a session ends, `addSessionToMemory` is called:

1. Fetch committed session events via `SessionHistoryService`.
2. Build a conversation text excerpt (capped at 8,000 chars, most-recent-first, with a trimming note when history is truncated).
3. Retrieve the 15 existing memories most semantically similar to the excerpt.
4. Invoke the `memory-agent` community expert (single-turn, ephemeral session) with the conversation and existing memories as context. The agent produces structured `ADD / UPDATE / DELETE / NOOP` decisions enforced by `responseFormat`.
5. Apply decisions: upsert or delete records in Qdrant.

### Retrieval

`searchMemory(agentId, userId, query)` performs a semantic search over the user's memories and returns matching `MemoryEntry` objects. The embedding model is read from `InfraConfig.embeddingModelId`; if blank, search returns empty.

### Configuration dependency

Memory extraction is skipped silently if:
- the `memory-agent` expert is absent from `CommunityRegistry`, or
- `InfraConfig.embeddingModelId` is blank (retrieval returns empty; extraction cannot find existing memories to reconcile).

## 2.10 Knowledge Indexing

`SessionRunner` inspects each file attachment on an incoming `UserMessage`:

- **Text files**: indexed synchronously into Qdrant via `KnowledgeService.create(IndexRequest)`. The knowledge item is scoped to the current session (`grants: ["S/<sessionId>"]`). On success a hint is appended to the user content instructing the model to use `search_knowledge` with the returned `knowledgeId`.
- **Binary files**: copied to cloud storage via `CloudStorageArtifactService` and left as artifacts accessible through the standard ADK artifact API.

The `search_knowledge` tool executes a semantic search over indexed chunks, filterable by `knowledgeIds` or scoped to all knowledge for the agent. Results include chunk text, pagination metadata, and total count.

Embedding model resolution for knowledge search: `agent.knowledgeSettings.embeddingModelId` → `InfraConfig.embeddingModelId`.

## 2.11 Session Rollback

`POST /v1/agent/session/{sessionId}/rollback?runId=<runId>` rewinds the session to the state it held before the specified run. The `SessionActor` handles a `RollbackCommand` which:

1. Identifies the event boundary for the given `runId` in the actor's replay state.
2. Truncates the event log to that boundary.
3. Records a `RollbackFact` so `SessionHistoryServiceImpl` can persist the rollback.

Rollback is non-destructive with respect to prior runs: only events belonging to the target run and any runs after it are removed.

## 2.12 Service Exposure: Local Bean First, gRPC Fallback

`@MicroService` interfaces are resolved by `MicroServiceClientProviderImpl`:

- if a local CDI implementation exists, use it
- otherwise create a dynamic gRPC proxy (`MicroServiceInvocationHandler`)

Server side (`GRPCServerImpl`):

- scans CDI beans for `@MicroService` interfaces
- dispatches requests by service + method name
- serializes args/results as JSON payloads over a gRPC stream
- executes on a virtual-thread-backed executor
