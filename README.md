# Agent Engine

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](#)

Agent Engine is a production-ready, highly modular Java 25/Quarkus runtime for building and orchestrating LLM-powered agents.

Built on top of `quarkus-langchain4j`, it provides a pluggable tool system, configurable agent definitions, scalable context management, and multiple interface modules (CLI and REST) for seamless interaction with your custom agents over gRPC or REST.

---

## 🚀 Quick Start

Agent Engine is deployed through Kubernetes-native Helm charts under [`k8s/`](/Users/rhp/Projects/agent-engine/k8s).

Deploy the standard stack:

```bash
./k8s/scripts/deploy.sh
```

This is the single deployment command for the application workloads. It builds the required service images and applies the Helm charts for `runtime`, `core`, and `rest`.

Sync infra, model, and agent data when needed:

```bash
./k8s/scripts/seed-configs.sh
```

Tear it down:

```bash
./k8s/scripts/cleanup.sh
```

Build service images manually with the shared Dockerfile when you need a custom image workflow:

```bash
docker build --build-arg SERVICE_MODULE=runtime -f docker/Dockerfile .
docker build --build-arg SERVICE_MODULE=core -f docker/Dockerfile .
docker build --build-arg SERVICE_MODULE=interfaces/rest -f docker/Dockerfile .
```

---

## 🛠 Building & Testing

Compile the project and build executable uber-jars (skipping tests for speed):

```bash
./gradlew clean build -x test
```

Run unit tests only:

```bash
./gradlew test
```

Run integration tests (opt-in):

```bash
./gradlew integrationTest
```

### Test Conventions

- Unit tests:
  - Source set: `src/test/java`
  - Class naming: `<ClassName>Test`
  - Method naming: `should<Behavior>When<Condition>`
- Integration tests:
  - Source set: `src/integrationTest/java`
  - Class naming: `<FeatureName>IT` or `<FeatureName>IntegrationTest`
  - Uses `@QuarkusTest` with container-backed resources where runtime wiring matters.

### Mock vs Container Policy

- Use mocks/fakes for unit tests focused on pure logic, branching, and service delegation.
- Use real Quarkus runtime + Testcontainers for integration tests that validate persistence, transport, and end-to-end wiring.

> **Note:** Test coverage reports are generated per module, for example at `runtime/build/reports/jacoco/test/html/index.html`.
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
- **Bootstrap Upserts**:
  `POST /v1/model/upsert` and `POST /v1/agent/upsert` with the model/agent JSON payloads

### Core Runtime Settings

- `mongodb.connection.string` / `MONGODB_CONNECTION_STRING`: Connection string for the Agent Config and Session store (default: `mongodb://localhost:27018`)
- `sessionStore.type: mongodb`: Persists context state, events, and app state natively in MongoDB.

Operational runtime config is expected to come from external Kubernetes config, typically a mounted `/config/application.properties` plus Secret-backed environment variables for sensitive values.

---

## 🧩 Tools & Customization

Agent Engine's power comes from its runtime tool system. Tools are provided through built-in `ToolProvider` / `ToolsetProvider` implementations plus auto-discovered runtime tools.

### Tooling Model

- A tool extends `com.agentengine.runtime.tools.Tool`, exposes an `execute(...)` method, and returns a `ToolDescriptor` describing its name, risk, and config schema.
- All registered tools are globally visible to all agents.
- `@ToolConstructor` selects which constructor should receive `toolConfig` values; otherwise the single constructor is used.
- `@ToolSchema` maps model arguments to parameters. `ToolContext` can be injected for runtime context.
- `ToolsetProvider` describes a user-facing suite name plus member descriptors; selecting the suite in `tools` activates the member tools at runtime.

### Built-in Tools

Agent Engine ships with the following built-in tool categories:

**Planning Tools** (suite: `planning`)
- `create_plan` - Create a new plan with tasks
- `add_task` - Add a task to the current plan
- `update_task_info` - Update task metadata
- `update_task_status` - Update task progress
- `start_task` - Mark a task as in-progress
- `complete_task` - Mark a task as completed
- `finish_plan` - Mark a plan as finished
- `view_plan` - View the current plan

**Agent Management** (suite: `agent_tools`)
- `spawn_agent` - Start a child agent session for a subtask
- `send_message` - Send a follow-up message to an existing child session
- `await_agent` - Wait for a child session to finish and collect its result

**File Operations** (auto-discovered)
- `read_file` - Read file contents with pagination (offset/limit)
- `list_dir` - List directory contents with depth control
- `grep_files` - Search files using regex patterns
- `apply_patch` - Apply unified diff patches to files with validation

**Shell & Execution**
- `shell_command` - Execute shell commands in a sandboxed environment

**Human-in-the-Loop**
- `human_in_the_loop` - Request human confirmation or input

**Web & Lookup**
- `web_search` - Search the web for information
- `web_lookup` - Fetch content from specific URLs

**Utilities**
- `echo` - Echo input back (useful for testing)

### Tool Execution Mode

By default, when an agent requests multiple tools in a single turn, they execute **in parallel** for better performance.
To enforce sequential execution (e.g., for tools with dependent side effects), set `toolExecutionMode: "SEQUENTIAL"` in the agent config:

```json
{
  "type": "DEFAULT",
  "toolExecutionMode": "SEQUENTIAL"
}
```

Options:
- `PARALLEL` (default) - Tools execute concurrently
- `SEQUENTIAL` - Tools execute one at a time in order

### Building Modules

To compile the active runtime and interface modules directly:

```bash
./gradlew :runtime:build :core:build :interfaces:rest:build
```

### Standard Agents

Agent Engine includes pre-configured agents for common use cases in `configs/agents/`:

| Agent | Purpose | Key Tools |
|-------|---------|-----------|
| `coding_agent` | General-purpose coding assistant with comprehensive safety guidelines | planning, file ops, shell |
| `review_agent` | Code review specialist with severity-ordered findings | read_file, grep, planning |
| `shell_agent` | Command-line execution with safety-first approach | shell_command, file ops |
| `web_research_agent` | Web research with citation standards | web_search, web_lookup |
| `story_orchestrator_sequential` | Multi-phase story pipeline orchestrator | orchestration |

Each agent has a comprehensive system prompt covering personality, safety guidelines, tool use patterns, and collaboration posture.

---

## 🏗 Architecture & Modules

The repository is structured to separate transport, control, and runtime concerns:

- **`runtime/`**: Agent execution, tool wiring, model factories, and context handling.
- **`runtime/api/`**: Runtime-facing service contracts.
- **`core/`**: Config CRUD, AG-UI mapping, and orchestration entrypoints.
- **`core/api/`**: Core service contracts used across modules.
- **`interfaces/rest/`**: REST gateway exposing user-facing HTTP endpoints.
- **`connectors/core/`**: Connector transport, auth, validation, and templating.
- **`util/*`**: Shared utilities for agents, MongoDB, microservice transport, and Pekko.
- **`configs/`**: Agent (`json/yaml`) and model (`json`) registry definitions.
- **`docker/`**: Container image build artifacts
- **`k8s/`**: Helm charts and Kubernetes deployment scripts
- **`scripts/`**: operational helper scripts

### MongoDB Config Store Details

- Configs are imported from `<configs>/agents` and `<configs>/models` into the `Agent` and `Model` collections under the `AGENT_ENGINE` database.
- The `_id` is the configuration filename without the extension; this ID is used universally across the API.
- If an `agentConfigPath` argument is provided in a request, it will dynamically override the global Mongo lookup.

---

## 📝 Additional Notes

- Agent config is flattened: `modelId`, `systemPrompt`, `tools`, and `contextStrategy` are first-class agent fields.
- Context strategy is agent-scoped; compaction affects model prompt construction, while full session event history remains intact.
- Compaction model resolution order: `contextStrategy.modelId` -> infra `default_model.compactionModelId` -> agent `modelId`.
- Title model resolution source: infra `default_model.titleModelId` (resolved per title-generation call).
- Enable built-in automated planning by listing `planning` under `tools`; the suite expands to `create_plan`, `update_plan`, `add_task`, `update_task_info`, `start_task`, `complete_task`, `finish_plan`, and `view_plan` at runtime.
- Multi-agent orchestration is runtime-native via `orchestrator` agents with `orchestrationMode=TRANSFER|SEQUENTIAL|PARALLEL`.
- `orchestrationMode=PARALLEL` emits aggregated orchestrator output only (no raw branch passthrough).
- Parallel branch success is strict: only terminal branches with `turnComplete=true` are counted successful.
- Parallel stopping policies:
  - `ALL_COMPLETE`: wait for all branches.
  - `FIRST_SUCCESS`: stop once the first successful branch completes.
  - `QUORUM`: stop once successful branches reach `quorum`.
- Parallel aggregation policies:
  - `CONCATENATE`: concatenate successful outputs in configured sub-agent order.
  - `BEST_EFFORT`: pick deterministic best successful output (longest text, stable tie-break by sub-agent order).
  - `MAJORITY_VOTE`: pick most frequent normalized successful output, tie-break with best-effort.
- If `FIRST_SUCCESS`/`QUORUM` targets are not met, runtime falls back to deterministic best-effort output and records a warning violation.
- Story pipelines are modeled as sequential orchestrators (see `configs/agents/story_orchestrator_sequential.json`).
- Guardrails are centralized via app-level callbacks (input/tool/output) with `allow/warn/block/escalate` semantics.
- Relevance scoring supports dual-prompt LLM evaluation (`relevance` + `irrelevance`) with combined score `(x + (100 - y)) / 2`, executed in parallel.
- Tool confirmations and clarification resumes use native runtime confirmation events (`requestConfirmation` / `adk_request_confirmation`). The REST resume endpoint sends native `FunctionResponse` payloads directly; no marker text protocol exists.
- Auto-discoverable tools use `@AgentTool` with constructor selection via `@ToolConstructor` and `@ToolParam`.
- Prompt templates (located in `runtime/src/main/resources/prompts`) are natively rendered via `Jinjava`.
- Squirrel-backed state machine helpers live in the runtime/core utility packages, returning success/failure results for builder-defined transitions.
- **Note on Local llama.cpp models**: Some `.gguf` models (e.g., `qwen3-coder-30b`) contain bugs in their embedded chat templates that cause `500 Server Errors` when parsing complex JSON schemas (like nested Arrays in `create_plan`). To fix this, provide an updated explicit template override via the `--chat-template-file` argument referencing the safe versions stored in `configs/models/templates/`.

### Enum Selection Guide

Use `UNKNOWN` as parser fallback only. Do not set `UNKNOWN` intentionally in agent configs.

| Enum | Values | When to use |
|---|---|---|
| `AgentType` | `DEFAULT`, `ORCHESTRATOR` | `DEFAULT` for most agents, `ORCHESTRATOR` for manager/coordinator agents with sub-agents. |
| `OrchestrationMode` | `TRANSFER`, `SEQUENTIAL`, `PARALLEL` | `TRANSFER` for LLM manager + transfer/AgentTool; `SEQUENTIAL` for fixed stage pipelines; `PARALLEL` for fan-out branch execution. |
| `GuardrailErrorMode` | `FAIL_CLOSED`, `FAIL_OPEN` | `FAIL_CLOSED` for safety-first production; `FAIL_OPEN` when availability is more important than strict safety. |
| `GuardrailRuleType` | `TEXT_CONTENT`, `TOOL_SAFETY`, `RELEVANCE` | Selects guardrail strategy: `TEXT_CONTENT` for text policy checks, `TOOL_SAFETY` for tool risk gating, and `RELEVANCE` for on-topic control. |
| `GuardrailStage` | `INPUT`, `TOOL`, `OUTPUT` | `INPUT` to filter user requests, `TOOL` to gate tool calls, `OUTPUT` to validate model responses before emission. |
| `GuardrailAction` | `ALLOW`, `WARN`, `BLOCK`, `ESCALATE` | `ALLOW` pass-through, `WARN` pass-through with signal, `BLOCK` hard stop, `ESCALATE` human-in-the-loop intervention path. |
| `RelevanceMode` | `STEER_THEN_BLOCK`, `STEER_ONLY`, `STEER_THEN_ALLOW` | `STEER_THEN_BLOCK` for balanced safety, `STEER_ONLY` for low-friction UX, `STEER_THEN_ALLOW` to retry briefly then continue. |
| `RelevanceAnchorStrategy` | `RECENT_USER`, `LATEST_USER_AND_PLAN` | `RECENT_USER` for conversational continuity, `LATEST_USER_AND_PLAN` for planning workflows. |
| `ToolRiskLevel` | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | Classify tools by side-effect sensitivity so `ToolSafetyGuardrail` can enforce minimum/maximum risk policy. |
| `ToolExecutionMode` | `PARALLEL`, `SEQUENTIAL` | `PARALLEL` (default) for concurrent tool execution; `SEQUENTIAL` when tools have dependent side effects that must run in order. |
| `ParallelAggregationPolicy` | `CONCATENATE`, `BEST_EFFORT`, `MAJORITY_VOTE` | `CONCATENATE` preserves all successful branch outputs in order; `BEST_EFFORT` chooses one deterministic best output; `MAJORITY_VOTE` chooses the most frequent normalized successful output. |
| `ParallelStoppingPolicy` | `ALL_COMPLETE`, `FIRST_SUCCESS`, `QUORUM` | `ALL_COMPLETE` waits all branches; `FIRST_SUCCESS` stops at first successful branch (`turnComplete=true`); `QUORUM` stops when successful branches reach configured `quorum`. |
