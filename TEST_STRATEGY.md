# Test Strategy

## Scope

- Unit-test the core engine utilities (`EngineUtils`, `CollectionUtils`, `TemplateUtils`), state management, configuration loading (`ConfigLoader`, `ResourceUtils`), and context assembly (`BaseContextBuilder`) in the `engine` module.
- Exercise engine behaviors with fake models/tools to validate tool orchestration, retries, and session updates without hitting real models or IO.

## Unit vs Integration

- **Unit tests**: pure functions, parsing/validation, session store, template rendering, and engine behavior using in-memory fakes.
- **Integration tests**: excluded for now (Quarkus runtime, CLI loop, model providers) to keep tests fast and deterministic.

## Bug Fix Workflow

- Write a unit test that reproduces the bug and fails.
- Implement the fix.
- Rerun the test suite to confirm the test passes.

## Mocking & Fixtures

- Use in-memory fakes for `LLMModel`, `SessionStore`, and `Tool`.
- Use JUnit `@TempDir` for filesystem-backed config loading.
- Avoid network calls and external model invocations.

## Key Risks & Focus Areas

- Parsing correctness (tool request extraction, JSON payload handling, thought blocks).
- Error handling (invalid configs, invalid tool requests, malformed JSON).
- Session consistency (IDs assigned, tool executions recorded).
- Tool orchestration boundaries (missing tool calls, retry limits).
