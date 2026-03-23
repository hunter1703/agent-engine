# 2. Runtime Architecture

## 2.1 Core Services

Implemented in `engine/src/main/java/com/agentengine/engine/services`:

- `AgentServiceImpl`: CRUD/query for agent configs
- `ModelServiceImpl`: CRUD/query for model configs
- `SessionServiceImpl`: session CRUD + title updates + deletion event publishing
- `AgentExecutionServiceImpl`: runtime orchestration and streaming execution

`AgentExecutionServiceImpl` is the execution center.

## 2.2 Runtime Lifecycle (`AgentExecutionServiceImpl`)

For each request:

1. Resolve target session (existing or new).
2. Resolve effective agent config (from request/session).
3. Acquire or create `AgentSessionRuntime` from ref-counted cache.
4. Build ADK `Runner` over app + plugins + resumability config.
5. Execute with `RunConfig` in SSE streaming mode.
6. On completion, generate/update session title.
7. Release runtime reference.

Runtime cache behavior:

- Name: `agent-runtime`
- Idle eviction: 30 minutes
- Periodic cleanup: 30 seconds

## 2.3 Agent Construction

`AgentProvider` selects `AgentFactory` by agent type.

- `default` -> standard LLM agent (`DelegatedAgent` around ADK `LlmAgent`)
- `orchestrator` -> `OrchestratorAgentFactory`

Orchestrator branches:

- `TRANSFER`: handoff-capable manager agent with native transfer targets
- `MANAGER`: custom `ManagerAgent` that runs an internal manager LLM plus visible child agent invocations with pairwise sidecar sessions
- `SEQUENTIAL`: ADK `SequentialAgent`
- `PARALLEL`: custom `ParallelAgent`

## 2.4 Parallel Orchestration Semantics

`ParallelAgent` executes sub-agents concurrently and emits only the aggregated orchestrator output.

Stopping policies:

- `ALL_COMPLETE`
- `FIRST_SUCCESS`
- `QUORUM`

Aggregation policies:

- `CONCATENATE`
- `BEST_EFFORT`
- `MAJORITY_VOTE`

If policy target cannot be satisfied, runtime falls back to deterministic best-effort and records a violation in run state.

## 2.5 Model Provisioning

`ModelProvider` resolves model config from `ModelRepository`, picks a `ModelFactory` by model type, and caches instantiated model clients.

Cache behavior:

- Name: `model-provider`
- Idle eviction: 15 minutes
- Cleanup interval: 60 seconds
- Auto-close on eviction if model implements `AutoCloseable`

## 2.6 Plugins Applied to Every Run

Execution creates a `PluginGroup` containing:

- `GuardrailPlugin`
- `ContextManagementPlugin`

Request/response processors now live in the engine-owned ADK flow class `EngineFlow` rather than in a plugin-owned model pipeline.

Flow-owned request processors:
- `CorrectionProcessor`
- `PlanningRequestProcessor`

Flow-owned response processors:
- `ToolCallSanitizationResponseProcessor`
- `PlanLoopResponseProcessor`

Terminal step/run semantics now follow ADK event semantics directly:
- terminal event = `Event.finalResponse()` or `EventActions.endInvocation()`
- the engine no longer synthesizes `turnComplete` or reorders response parts after the model

### ContextManagementPlugin responsibilities

- context manager prompt rebuild

### GuardrailPlugin responsibilities

- input guardrails before model invocation
- tool guardrails before tool call execution
- output guardrails post-model generation
- optimistic mode support with async output guardrail futures
- native escalation behavior for tool confirmations and synthetic internal human-input tool calls

## 2.7 Context Management

Context manager selection is strategy-driven:

- `compaction`
- `last_n`
- `none`

Context manager is applied in `ContextManagementPlugin.beforeModelCallback`, replacing request
content with a rebuilt prompt sequence.

## 2.8 Session Persistence Contract

`AgentSessionRepository` implements ADK `BaseSessionService` and maps ADK `Session` objects to persisted `AgentSession` entities.

- events are serialized/deserialized via JSON maps
- event history can be filtered by `numRecentEvents` or timestamp
- append only persists finalized (non-partial) event updates

## 2.9 Service Exposure: Local Bean First, gRPC Fallback

`@MicroService` interfaces are resolved by `MicroServiceClientProviderImpl`:

- if local CDI implementation exists, use it
- otherwise create a dynamic gRPC proxy

Server side (`GRPCServerImpl`):

- scans CDI beans for `@MicroService` interfaces
- dispatches request by service+method name
- serializes args/results as JSON payloads over gRPC stream
- executes on virtual-thread-backed executor
