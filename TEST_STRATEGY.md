# Test Strategy

## Scope
- Unit-test the core engine utilities (`EngineUtils`, `CollectionUtils`, `TemplateUtils`), state management (`InMemorySessionStore`), configuration loading (`ConfigLoader`, `ResourceUtils`), and context assembly (`BaseContextBuilder`).
- Exercise `HybridEngine` behaviors with fake models/tools to validate tool orchestration, retries, and session updates without hitting real models or IO.

## Unit vs Integration
- **Unit tests**: pure functions, parsing/validation, session store, template rendering, and `HybridEngine` behavior using in-memory fakes.
- **Integration tests**: excluded for now (Quarkus runtime, CLI loop, model providers) to keep tests fast and deterministic.

## Mocking & Fixtures
- Use in-memory fakes for `LLMModel`, `SessionStore`, and `AgentTool`.
- Use JUnit `@TempDir` for filesystem-backed config loading.
- Avoid network calls and external model invocations.

## Key Risks & Focus Areas
- Parsing correctness (tool request extraction, JSON payload handling, thought blocks).
- Error handling (invalid configs, invalid tool requests, malformed JSON).
- Session consistency (IDs assigned, tool executions recorded).
- Tool orchestration boundaries (missing tool calls, retry limits).
