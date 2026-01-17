# Agent Engine

## Overview
This project is a standalone Java 21/Quarkus agent engine that uses
`quarkus-langchain4j` for model integrations.

## Project Layout
- `src/main/java/com/agentengine/engine`: core engine, config, context, state, tooling
- `src/main/java/com/agentengine/agents`: agent definitions (e.g., `shell_agent`)
- `src/main/java/com/agentengine/cli`: JSON-over-stdio CLI/runtime
- `models/`: model registry configs (JSON/YAML)
- `agents/`: agent configs (JSON/YAML) used by the default builder
- `examples/`: sample configs and CLI command payloads

## Quick Start
```bash
./gradlew build
```

## Testing
```bash
./gradlew test
```

- Coverage report: `build/reports/jacoco/test/html/index.html`
- Add new tests under `src/test/java` using JUnit5 + AssertJ

To run the stdio server:
```bash
./gradlew run --args="server"
```

## Notes
- Agent configs use the Java schema (`engine` holds prompt + model keys; `context` holds `summarizer_model`).
- Model configs are loaded from `models/<model_key>.*`.
- Tools are discovered via Java ServiceLoader entries under `META-INF/services`.
- Prompt templates live in `src/main/resources/prompts` and render via Jinjava.
