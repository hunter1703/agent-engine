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

Agent Engine's power comes from its modular architecture. Tools are external JARs that implement `com.agentengine.engine.api.tools.ToolProvider` via `META-INF/services` and are dynamically added to the runtime classpath.

At runtime, the engine loads all plugin JARs found in the `PLUGIN_DIR` (or `./plugins` by default).

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
- Enable built-in automated planning by adding `planning` to `tools.standardTools` (or `tools.enabled`). This automatically expands the agent's capabilities to use `create_plan`, `update_plan_info`, `revise_current_plan`, and `finish_plan`.
- Tools are discovered strictly via Java `ServiceLoader` entries under `META-INF/services`.
- Prompt templates (located in `engine/src/main/resources/prompts`) are natively rendered via `Jinjava`.
