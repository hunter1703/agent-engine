# Agent Engine

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](#)

Agent Engine is a production-ready, highly modular Java 21/Quarkus runtime for building and orchestrating LLM-powered agents.

Built on top of `quarkus-langchain4j`, it provides a pluggable tool system, configurable agent definitions, scalable context management, and multiple interface modules (CLI and REST) for seamless interaction with your custom agents over gRPC or REST.

---

## 🚀 Quick Start

Agent Engine can be run locally in development mode (monolithic Quarkus Dev mode) or entirely containerized as production-ready microservices.

Both modes automatically provision the required local MongoDB instance via Docker Compose.

### Development Mode

Boot the entire engine with hot-reload and optionally bootstrap initial models and agents:

```bash
./deploy/deploy.sh dev [--bootstrap]
```

### Production Mode (Microservices)

Run as separate, production-ready microservices (Core Engine on port 8081/9000, REST API on port 8080) and optionally bootstrap the database:

```bash
./deploy/deploy.sh production [--bootstrap]
```


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
- **Codex CLI Compatible Stream**:
  `POST /agent/responses` with `{ "agentId": "...", "sessionId": "...", "message": "..." }`

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
2. Build and assemble all plugins:
   ```bash
   ./gradlew preparePlugins
   ```
   _The `preparePlugins` task compiles each project under `plugins/` and automatically copies the resulting `_-plugin.jar`artifacts into the top-level`plugins/` directory so the runtime can discover them.\*

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

- Agent configurations use the native Java schema: `engine` defines the system prompt and model keys, while `context` defines the summarizer model.
- Enable built-in automated planning by listing `planning` under `model.tools`; the suite expands to `create_plan`, `update_plan_info`, `revise_current_plan`, `update_subtask_state`, `finish_plan`, and `view_current_plan` at runtime.
- Plugin tools are discovered via Java `ServiceLoader` entries under `META-INF/services` for `ToolProvider` implementations.
- Auto-discoverable tools use `@AgentTool` with constructor selection via `@ToolConstructor` and `@ToolParam`.
- Prompt templates (located in `engine/src/main/resources/prompts`) are natively rendered via `Jinjava`.
- **Note on Local llama.cpp models**: Some `.gguf` models (e.g., `qwen3-coder-30b`) contain bugs in their embedded chat templates that cause `500 Server Errors` when parsing complex JSON schemas (like nested Arrays in `create_plan`). To fix this, provide an updated explicit template override via the `--chat-template-file` argument referencing the safe versions stored in `configs/models/templates/`.
