# Agent Engine

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](#)

Agent Engine is a production-ready, highly modular Java 25/Quarkus runtime for building and orchestrating LLM-powered agents.

Built on top of `quarkus-langchain4j`, it provides a pluggable tool system, configurable agent definitions, scalable context management, and multiple interface modules (CLI and REST) for seamless interaction with your custom agents over gRPC or REST.

---

## 🚀 Quick Start

Agent Engine can be run locally in development mode (monolithic Quarkus Dev mode) or entirely containerized as production-ready microservices.

Both modes automatically provision the required local MongoDB instance via Docker Compose.

### Development Mode

Boot the entire engine with hot-reload and optionally bootstrap initial models and agents:

```bash
./deploy/deploy.sh dev [--bootstrap] [--clean]
```

### Production Mode (Microservices)

Run as separate, production-ready microservices (Core Engine on port 8081/9000, REST API on port 8080) and optionally bootstrap the database:

```bash
./deploy/deploy.sh production [--bootstrap] [--clean]
```

Use `--clean` to run a full Gradle clean before building.


### Stopping Services

To stop all background services (Engine, REST) and the MongoDB infrastructure:

```bash
./deploy/stop.sh
```

---

## 🛠 Building & Testing

Compile the project and build executable uber-jars (skipping tests for speed):

```bash
./gradlew clean build -x test
```

Run the full, production-ready test suite (automatically boots local test infrastructure via Quarkus DevServices):

```bash
./gradlew clean test --no-build-cache
```

> **Note:** Test coverage reports are generated automatically at `engine/build/reports/jacoco/test/html/index.html`.
> **Note:** Gradle parallel execution and configuration-on-demand are enabled for faster builds; if you hit Quarkus plugin sync issues in your environment, temporarily disable them in `gradle.properties`.

---

## 📡 Service Endpoints & Integration

Interact with your agents using the unified REST API:

- **Invoke an Agent**:
  `POST /agent/invoke`
  ```json
  {
    "type": "INVOKE_AGENT",
    "agentId": "example_agent",
    "sessionId": "session_123",
    "message": "Hello!"
  }
  ```
- **Stream SSE Events**:
  `POST /agent/events` with `{ "agentId": "...", "sessionId": "...", "message": "..." }`
- **Resume Paused Session (SSE)**:
  `POST /agent/session/{sessionId}/resume/events` with `{ "message": "human clarification" }`
- **Codex CLI Compatible Stream**:
  `POST /agent/responses` with `{ "agentId": "...", "sessionId": "...", "message": "..." }`
- **Bootstrap Upserts**:
  `POST /v1/model/upsert` and `POST /v1/agent/agent/upsert` with the model/agent JSON payloads

### Core Runtime Settings

- `PLUGIN_DIR`: Directory containing plugin JARs (default: `plugins`)
- `MONGODB_CONNECTION_STRING`: Connection string for the Agent Config and Session store (default: `mongodb://localhost:27017`)
- `sessionStore.type: mongodb`: Persists context state, events, and app state natively in MongoDB.

---

## 🧩 Plugins & Custom Tools

Agent Engine's power comes from its modular architecture. Tools are provided by `com.agentengine.engine.api.tools.ToolProvider` implementations. Built-in providers include the auto-discovery provider for `@AgentTool` classes, while plugins register providers via `META-INF/services`.

At runtime, the engine loads all plugin JARs found in the `PLUGIN_DIR` (or `./plugins` by default).

### Tooling Model

- A tool implements `com.agentengine.engine.api.tools.Tool`, exposes an `execute(...)` method, and returns a `ToolDescriptor` describing its name, scope, and config schema.
- `ToolDescriptor.agentIds` is a scope filter: empty or `ALL` means globally available; otherwise the tool is scoped to the listed agent IDs.
- `@AgentTool` marks auto-discoverable tools. `@ToolConstructor` selects which constructor should receive `toolConfig` values; otherwise the single constructor is used.
- `@ToolParam` maps constructor params to config keys. When omitted, parameter names are used (requires compilation with `-parameters`).
- `ToolParam.AGENT_CONTEXT` or an `AgentContext` parameter injects the current execution context.
- `ToolSuite` describes a user-facing suite name plus `toolNames()`; selecting the suite in `model.tools` expands to the member tools at runtime.

### Building Plugins

To compile custom plugins (like the `shell-agent` or `echo-agent`) into your environment:

1. Build the core engine JAR first:
   ```bash
   ./gradlew :engine:jar
   ```
2. Build the plugin project and copy the resulting `*-plugin.jar` into `plugins/`:
   ```bash
   ./gradlew -p plugins/<plugin-project> assemble
   ```

---

## 🏗 Architecture & Modules

The repository is structured to separate interface transports from the core LLM execution engine:

- **`engine/`**: The core execution library (config, context, state mapping, and tooling).
- **`engine/client/`**: Shared client request/response GRPC buffers and models.
- **`interfaces/common/`**: Shared interface services and utilities.
- **`interfaces/rest/`**: REST service gateway exposing user-facing endpoints.
- **`plugins/`**: Optional, dynamically loaded tool/plugin JAR projects.
- **`configs/`**: Agent (`json/yaml`) and Model (`json`) registry configuration definitions.
- **`deploy/`**: Docker resources, `docker-compose.yaml`, and the unified deployment script.

### MongoDB Config Store Details

- Configs are imported from `<configs>/agents` and `<configs>/models` into the `Agent` and `Model` collections under the `AGENT_ENGINE` database.
- The `_id` is the configuration filename without the extension; this ID is used universally across the API.
- If an `agentConfigPath` argument is provided in a request, it will dynamically override the global Mongo lookup.

---

## 📝 Additional Notes

- Agent model configs default to `contextManagerConfig.type = summarize` (trigger/recency thresholds); older turns are compacted for model input while full session history remains unchanged.
- Compacted summaries are persisted separately in `SessionContextSummary` documents and are not written into session event history.
- Summary model resolution order: `contextManagerConfig.summaryModelId` -> infra `default_model.summaryModelId` -> agent `model.modelId`.
- Title model resolution order: infra `default_model.titleModelId` -> legacy infra `title_model.modelId`.
- Enable built-in automated planning by listing `planning` under `model.tools`; the suite expands to `create_plan`, `update_plan`, `add_task`, `update_task_info`, `start_task`, `complete_task`, `finish_plan`, and `view_plan` at runtime.
- Enable built-in multi-agent orchestration by listing `multi_agent` under `model.tools`; the suite expands to `delegate_to_agent` (manager-as-tool delegation), `handoff_to_agent` (decentralized handoff intent), and `parallel_delegate` (parallel sectioning/voting with aggregation + stopping policies).
- `handoff_to_agent` persists handoff state and runtime auto-continues with the target agent after source completion using handoff message and next session id.
- Handoff continuation is protected by max-hop limits (`max_handoff_hops`) and emits in-process telemetry counters (`handoff_requested`, `handoff_continued`, `handoff_loop_blocked`).
- Output evaluation workflow is configurable via `outputEvaluation` in agent config and enforces retry/block stopping policy based on response relevance score thresholds.
- Relevance scoring supports dual-prompt LLM evaluation (`relevance` + `irrelevance`) with combined score `(x + (100 - y)) / 2`, executed in parallel.
- Plugin tools are discovered via Java `ServiceLoader` entries under `META-INF/services` for `ToolProvider` implementations.
- Auto-discoverable tools use `@AgentTool` with constructor selection via `@ToolConstructor` and `@ToolParam`.
- Prompt templates (located in `engine/src/main/resources/prompts`) are natively rendered via `Jinjava`.
- Squirrel-backed state machine helpers live in `com.agentengine.engine.utils`, returning success/failure results for builder-defined transitions.
- **Note on Local llama.cpp models**: Some `.gguf` models (e.g., `qwen3-coder-30b`) contain bugs in their embedded chat templates that cause `500 Server Errors` when parsing complex JSON schemas (like nested Arrays in `create_plan`). To fix this, provide an updated explicit template override via the `--chat-template-file` argument referencing the safe versions stored in `configs/models/templates/`.

### Enum Selection Guide

Use `UNKNOWN` as parser fallback only. Do not set `UNKNOWN` intentionally in agent configs.

| Enum | Values | When to use |
|---|---|---|
| `AgentType` | `DEFAULT`, `STORY`, `ORCHESTRATOR`, `WORKER` | `DEFAULT` for most agents, `STORY` for story flow, `ORCHESTRATOR` for manager-style delegation, `WORKER` for delegated specialists. |
| `GuardrailExecutionMode` | `SYNC`, `OPTIMISTIC` | `SYNC` for deterministic blocking before execution, `OPTIMISTIC` to reduce latency and tripwire-cancel on block. |
| `GuardrailErrorMode` | `FAIL_CLOSED`, `FAIL_OPEN` | `FAIL_CLOSED` for safety-first production; `FAIL_OPEN` when availability is more important than strict safety. |
| `GuardrailRuleType` | `TEXT_CONTENT`, `TOOL_SAFETY`, `RELEVANCE` | Selects guardrail strategy: `TEXT_CONTENT` for text policy checks, `TOOL_SAFETY` for tool risk gating, and `RELEVANCE` for on-topic control. |
| `GuardrailStage` | `INPUT`, `TOOL`, `OUTPUT` | `INPUT` to filter user requests, `TOOL` to gate tool calls, `OUTPUT` to validate model responses before emission. |
| `GuardrailAction` | `ALLOW`, `WARN`, `BLOCK`, `ESCALATE` | `ALLOW` pass-through, `WARN` pass-through with signal, `BLOCK` hard stop, `ESCALATE` human-in-the-loop intervention path. |
| `RelevanceMode` | `STEER_THEN_BLOCK`, `STEER_ONLY`, `STRICT_BLOCK` | `STEER_THEN_BLOCK` for balanced behavior, `STEER_ONLY` for low-friction UX, `STRICT_BLOCK` for strict domain policy. |
| `RelevanceAchoreStrategy` | `RECENT_USER`, `LATEST_USER_AND_PLAN` | `RECENT_USER` for conversational continuity (`recency=1` is latest-only), `LATEST_USER_AND_PLAN` for planning workflows. |
| `EvaluatorStopPolicy` | `RETRY_THEN_ALLOW`, `RETRY_THEN_BLOCK` | `RETRY_THEN_ALLOW` to avoid hard failures, `RETRY_THEN_BLOCK` for quality-gated outputs. |
| `ToolRiskLevel` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | Classify tools by side-effect sensitivity so `ToolSafetyGuardrail` can enforce minimum/maximum risk policy. |
| `ParallelMode` | `SECTIONING`, `VOTING` | `SECTIONING` for independent subtasks; `VOTING` to run same prompt multiple times for consensus. |
| `ParallelAggregationPolicy` | `CONCATENATE`, `BEST_EFFORT`, `MAJORITY_VOTE` | `CONCATENATE` keep all outputs, `BEST_EFFORT` pick one best branch output, `MAJORITY_VOTE` choose consensus response. |
| `ParallelStoppingPolicy` | `ALL_COMPLETE`, `FIRST_SUCCESS`, `QUORUM` | `ALL_COMPLETE` for full coverage, `FIRST_SUCCESS` for latency, `QUORUM` for balanced speed/confidence. |
