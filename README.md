# Agent Engine

## Overview
This project is a standalone Java 21/Quarkus agent engine that uses
`quarkus-langchain4j` for model integrations.

## Project Layout
- `engine/src/main/java/com/agentengine/engine`: core engine, config, context, state, tooling
- `engine/client/src/main/java/com/agentengine/client`: shared client request models
- `interfaces/common/src/main/java/com/agentengine/interfaces`: shared interface services/utilities
- `interfaces/rest/src/main/java/com/agentengine/api`: REST service layer
- `plugins/`: optional tool/plugin projects (build into JARs)
- `configs/agents`: agent configs (JSON/YAML)
- `configs/models`: model registry configs (JSON)
- `deploy/docker`: docker resources
- `examples/`: sample configs and request payloads

## Modules
- `engine`: core engine library
- `engine:client`: shared request/response models
- `interfaces`: umbrella module for transports
  - `interfaces:common`: shared interface services/utilities
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
- `POST /agent/invoke` with `{ "type": "INVOKE_AGENT", "agentName": "...", "agentConfigPath": "...", "sessionId": "...", "message": "..." }`
  - Use `type: "BUILD_PROMPT"` to return the assembled prompt.
- `POST /agent/events` with `{ "agentName": "...", "agentConfigPath": "...", "sessionId": "...", "message": "..." }` for SSE event stream

Runtime settings:
- `PLUGIN_DIR` (env or system property) points to a directory of plugin JARs (default `plugins`)
- `MONGODB_CONNECTION_STRING` is read from the environment (default `mongodb://localhost:27000`)

## Testing
```bash
./gradlew :engine:test
```

- Coverage report: `engine/build/reports/jacoco/test/html/index.html`
- Add new tests under `<module>/src/test/java` using JUnit5 + AssertJ

## Plugins
Plugins are external JARs that implement `com.agentengine.engine.tools.ToolProvider` via
`META-INF/services` and are added to the runtime classpath.

At runtime, the engine loads plugin JARs from `PLUGIN_DIR` (or `./plugins` by default).
Agent configs are loaded from MongoDB (if configured) or passed in via `agentConfigPath`.

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
MONGODB_CONNECTION_STRING=mongodb://localhost:27000 ./gradlew deployEngine
```

### Convenience script
```bash
./scripts/run-rest-dev.sh
./scripts/run-rest-dev.sh --force ./configs
```

If you need a non-default MongoDB connection string, set:
`MONGODB_CONNECTION_STRING=mongodb://localhost:27000` before running.

### Master Gradle task
```bash
./gradlew deployEngine
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
  collections under the `AGENT_ENGINE` database (setup script uses `CONFIG_DB_NAME`).
- `_id` is the config filename without extension; this is the ID used everywhere.
- When MongoDB is configured (`MONGODB_CONNECTION_STRING`), the agent service loads
  configs by `agentName` from MongoDB (database name is currently fixed to `AGENT_ENGINE`).
- If `agentConfigPath` is provided in the request, it overrides Mongo lookup.
- The setup script accepts an optional config path argument and defaults to `./configs`.
- The setup script imports JSON configs and requires `jq`.

## Notes
- Agent configs use the Java schema (`engine` holds `systemPrompt` + model keys; `context` holds `summarizerModel`).
- Model configs are loaded by ID from MongoDB (imported from `configs/models`).
- LLAMA_CPP configs can auto-start `llama-server` when `serverCommand` is provided.
- Tools are discovered via Java ServiceLoader entries under `META-INF/services`.
- Prompt templates live in `engine/src/main/resources/prompts` and render via Jinjava.
