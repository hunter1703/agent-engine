# Agent Engine

## Overview
This project is a standalone Java 21/Quarkus agent engine that uses
`quarkus-langchain4j` for model integrations.

## Project Layout
- `engine/src/main/java/com/agentengine/engine`: core engine, config, context, state, tooling
- `engine/client/src/main/java/com/agentengine/client`: shared client request models
- `interfaces/cli/src/main/java/com/agentengine/cli`: JSON-over-stdio CLI/runtime
- `interfaces/rest/src/main/java/com/agentengine/api`: REST service layer
- `plugins/`: optional tool/plugin projects (build into JARs)
- `configs/agents`: agent configs (JSON/YAML)
- `configs/models`: model registry configs (JSON/YAML)
- `deploy/docker`: docker resources
- `examples/`: sample configs and CLI command payloads

## Modules
- `engine`: core engine library
- `engine:client`: shared request/response models
- `interfaces`: umbrella module for transports
  - `interfaces:cli`: stdio interface (depends on `engine`, `engine:client`)
  - `interfaces:rest`: REST service (depends on `engine`, `engine:client`)

## Quick Start
```bash
./gradlew build
```

## Service
Run the REST service locally:
```bash
./gradlew :interfaces:rest:quarkusDev
```

Or use the root shortcut:
```bash
./gradlew deployEngine
```

Endpoints:
- `POST /agent/invoke` with `{ "agentName": "...", "agentConfigPath": "...", "sessionId": "...", "message": "..." }`
  - Use `type: "BUILD_PROMPT"` to return the assembled prompt.
- `POST /agent/events` with `{ "agentName": "...", "agentConfigPath": "...", "sessionId": "...", "message": "..." }` for SSE event stream

Environment defaults:
- `AGENT_NAME` sets the default agent name
- `AGENT_CONFIG_PATH` sets the default config path
- `CONFIG_DIR` points to the config root (default `configs`)
- `CONFIG_DB_NAME` sets the MongoDB database name (default `AGENT_ENGINE`)
- `PLUGIN_DIR` points to a directory of plugin JARs (default `plugins`)

## Testing
```bash
./gradlew :engine:test
```

- Coverage report: `build/reports/jacoco/test/html/index.html`
- Add new tests under `src/test/java` using JUnit5 + AssertJ

## CLI
To run the stdio server:
```bash
./gradlew :interfaces:cli:run --args="server"
```

## Plugins
Plugins are external JARs that implement `com.agentengine.engine.tools.ToolProvider` via
`META-INF/services` and are added to the runtime classpath.

At runtime, the engine loads plugin JARs from `PLUGIN_DIR` (or `./plugins` by default).
Agent configs live under `CONFIG_DIR/agents/<agent>.json|yaml` by default.

Build the shell tool plugin:
```bash
cd plugins/shell-agent
./gradlew build
```

Or use the helper script:
```bash
./scripts/build-plugin.sh shell-agent
```

Build the echo plugin:
```bash
cd plugins/echo-agent
./gradlew build
```

Build the engine JAR first so the plugin compiles:
```bash
./gradlew :engine:jar
```

Build and copy plugin JARs into the runtime plugin directory:
```bash
./gradlew preparePlugins
```

`preparePlugins` builds each plugin under `plugins/` and copies the resulting `*-plugin.jar`
artifacts into the top-level `plugins/` directory so the runtime `PLUGIN_DIR` can load them.

Run the build tooling tests:
```bash
./gradlew -p buildSrc test
```

## Deployment

Systemd service template:
- `deploy/agent-engine.service`
- Deployment guide: `deploy/README.md`

## MongoDB Config Store

### Quickstart
```bash
./deploy/docker/setup-mongo.sh ./configs
./deploy/docker/setup-mongo.sh --force ./configs
MONGODB_CONNECTION_STRING=mongodb://localhost:27000 \
CONFIG_DB_NAME=AGENT_ENGINE \
PLUGIN_DIR=./plugins \
./gradlew restStack
```

### Convenience script
```bash
./scripts/run-rest-dev.sh
./scripts/run-rest-dev.sh --force ./configs
```

### Master Gradle task
```bash
./gradlew restStack
./gradlew deployEngine -PmongoArgs="--force ./configs"
```

The `deployEngine` task skips starting Quarkus if the REST port is already in use (defaults to
`8080`, or `QUARKUS_HTTP_PORT` if set), so reruns are safe.

Invoke by agent id (Mongo `_id`):
```bash
curl -N -X POST http://localhost:8080/agent/events \
  -H 'Content-Type: application/json' \
  -d '{"agentName":"shell_agent","sessionId":"demo","message":"Run pwd and return output."}'
```

### Details
- The setup script builds a Mongo image from `deploy/docker/Dockerfile.mongodb` only if missing
  (use `--force` to rebuild) and starts a container named `agent-engine-mongodb`.
- Configs are imported from `<configs>/agents` and `<configs>/models` into `Agent` and `Model`
  collections under the `AGENT_ENGINE` database (override with `CONFIG_DB_NAME`).
- `_id` is the config filename without extension; this is the ID used everywhere.
- When MongoDB is configured (`MONGODB_CONNECTION_STRING`), the agent service loads
  configs by `agentName` from MongoDB.
- If `agentConfigPath` is provided in the request, it overrides Mongo lookup.
- The setup script accepts an optional config path argument and defaults to `./configs`.
- The setup script imports JSON configs and requires `jq`.

## Model Config Validation
```bash
./gradlew :engine:validateModelConfig --args="configs/models/qwq_32b.json"
```

Then place the resulting JAR into your deployment plugin directory and add it to the classpath
on startup.

## Notes
- Agent configs use the Java schema (`engine` holds `systemPrompt` + model keys; `context` holds `summarizerModel`).
- Model configs are loaded from `configs/models/<model_key>.*`.
- LLAMA_CPP configs can auto-start `llama-server` when `serverCommand` is provided.
- Tools are discovered via Java ServiceLoader entries under `META-INF/services`.
- Prompt templates live in `src/main/resources/prompts` and render via Jinjava.
