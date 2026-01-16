# Local Agent (Quarkus + LangChain4j)

## Overview
This project is the Java 21/Quarkus port of the Local Agent engine. It mirrors the Python
architecture while using `quarkus-langchain4j` for model integrations. The Python
implementation remains untouched in `~/Projects/local-agent`.

## Project Layout
- `src/main/java/com/localagent/engine`: core engine, config, context, state, tooling
- `src/main/java/com/localagent/agents`: agent definitions (e.g., `shell_agent`)
- `src/main/java/com/localagent/cli`: JSON-over-stdio CLI/runtime
- `models/`: model registry configs (JSON/YAML)
- `agents/`: agent configs (JSON/YAML) used by the default builder
- `examples/`: sample configs and CLI command payloads

## Quick Start
```bash
./gradlew test
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
