# Agent Engine

## Overview
This project is a standalone Java 21/Quarkus agent engine that uses
`quarkus-langchain4j` for model integrations.

## Project Layout
- `engine/src/main/java/com/agentengine/engine`: core engine, config, context, state, tooling
- `interfaces/cli/src/main/java/com/agentengine/cli`: JSON-over-stdio CLI/runtime
- `interfaces/rest/src/main/java/com/agentengine/api`: REST service layer
- `plugins/`: optional tool/plugin projects (build into JARs)
- `models/`: model registry configs (JSON/YAML)
- `plugins/<plugin>/config`: agent configs (JSON/YAML) shipped with plugins
- `examples/`: sample configs and CLI command payloads

## Modules
- `engine`: core engine library
- `interfaces`: umbrella module for transports
  - `interfaces:cli`: stdio interface (depends on `engine`)
  - `interfaces:rest`: REST service (depends on `engine`)

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
./gradlew restDev
```

Endpoints:
- `POST /agent/invoke` with `{ "agentName": "...", "agentConfigPath": "...", "sessionId": "...", "message": "..." }`
- `POST /agent/prompt` with `{ "agentName": "...", "agentConfigPath": "...", "sessionId": "..." }`
- `GET /agent/events?agentName=...&agentConfigPath=...&sessionId=...` for SSE event stream

Environment defaults:
- `AGENT_NAME` sets the default agent name
- `AGENT_CONFIG_PATH` sets the default config path
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
Agent configs live under `PLUGIN_DIR/config/<agent>.json|yaml` by default.

Build the shell tool plugin:
```bash
cd plugins/shell-agent
./gradlew build
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

Copy built plugin JARs into the runtime plugin directory:
```bash
./gradlew syncPlugins
```

`syncPlugins` copies `plugins/**/build/libs/*.jar` into the top-level `plugins/` directory
so the runtime `PLUGIN_DIR` can load them.

## Deployment
Build the REST app and container image:
```bash
./gradlew :interfaces:rest:build
docker build -f deploy/Dockerfile -t agent-engine:latest .
```

Systemd service template:
- `deploy/agent-engine.service`
- Deployment guide: `deploy/README.md`

## Model Config Validation
```bash
./gradlew :engine:validateModelConfig --args="models/qwq_32b.json"
```

Then place the resulting JAR into your deployment plugin directory and add it to the classpath
on startup.

## Notes
- Agent configs use the Java schema (`engine` holds prompt + model keys; `context` holds `summarizer_model`).
- Model configs are loaded from `models/<model_key>.*`.
- Tools are discovered via Java ServiceLoader entries under `META-INF/services`.
- Prompt templates live in `src/main/resources/prompts` and render via Jinjava.
