# Agent Guidelines

> [!IMPORTANT]
> This project is in an active development phase. Backward compatibility is not guaranteed, and breaking changes may occur frequently as we refine the core APIs and integration protocols.

## Project Summary
Agent Engine is a modular Java 25/Quarkus agent runtime built on LangChain4j. It provides a
plugin-based tool system, configurable agent definitions, and multiple interface modules (CLI
and REST) for interacting with agents.

## Project Goals
- Provide a production-ready, pluggable agent runtime with clear interfaces for custom agents,
  tools, context management, and persistence.
- Support local and hosted model backends through model registry configs and plugin-delivered
  agent configs.
- Offer lightweight interface modules (CLI and REST) to validate and extend the agent ecosystem.

## Commands

```bash
# Build (skip tests)
./gradlew clean build -x test

# Run tests
./gradlew test

# Integration tests (opt-in, requires Docker)
./gradlew integrationTest

# Deploy the standard Kubernetes stack
./k8s/scripts/deploy.sh

# Tear down the standard Kubernetes stack
./k8s/scripts/cleanup.sh

# Build a service image
docker build --build-arg SERVICE_MODULE=runtime -f docker/Dockerfile .
```

## Module Structure

| Module              | Purpose                                                         |
|---------------------|-----------------------------------------------------------------|
| `engine/`           | Core LLM execution: config, state, context, LangChain4j wiring |
| `engine/api/`       | Shared API beans, service interfaces, and event model           |
| `interfaces/rest/`  | REST gateway (port 8080); user-facing HTTP endpoints            |
| `interfaces/local/` | CLI interface for local agent interaction                       |
| `util/common/`      | Cross-module utility classes                                    |
| `util/mongodb/`     | MongoDB client factory, codec registry, encryption              |
| `util/ms/`          | Microservice transport utilities                                |
| `connectors/core/`  | Outbound HTTP transport and auth strategies                     |
| `configs/`          | Agent and model registry JSON/YAML definitions                  |
| `docker/`           | Shared container image build artifacts                          |
| `k8s/`              | Helm charts and Kubernetes deployment scripts                   |
| `scripts/`          | Operational helper scripts                                      |

## Testing Conventions

- Unit tests: `src/test/java`, class `<ClassName>Test`, method `should<Behavior>When<Condition>`
- Integration tests: `src/integrationTest/java`, class `<Feature>IT`; use `@QuarkusTest` with container-backed resources
- Use mocks/fakes for pure logic; use real containers (Testcontainers) when runtime wiring matters
- **Bug fix workflow**: When fixing a bug, first write a test that fails (demonstrating the bug), then fix the code, then verify the test passes

## Key Gotchas

- **llama.cpp chat template bug**: Some `.gguf` models (e.g. `qwen3-coder-30b`) cause `500` errors on nested JSON schemas. Fix: pass `--chat-template-file` pointing to the safe template in `configs/models/templates/`.
- **Compaction model resolution order**: `contextStrategy.modelId` → infra `default_model.compactionModelId` → agent `modelId`.
- **Session history source**: committed session events are reconstructed from the session actor's replay state rather than an embedded event blob on `AgentSession`.
- **Deferred work**: record follow-ups in `TODO.md`, not inline comments.
- **Enum rule**: all enums must include `UNKNOWN` and a `valueOfOrDefault` parser.
- **Commits and branches**: Never commit unless explicitly asked. Never create a separate branch unless explicitly asked. Always make changes directly on `main` and leave them unstaged so the user can review before staging or committing.

## Code Quality Philosophy

Write code that is **beautiful, easy to read, and architecturally elegant** — without sacrificing performance.
Aim for the solution that is simultaneously the simplest, the clearest, and the most efficient. Design for
the reader and the runtime equally.

- **Clarity over cleverness**: code should reveal its intent immediately; a reader unfamiliar with the method
  should understand what it does and why.
- **Earn every abstraction**: introduce an abstraction only when it has a clear name, a single responsibility,
  and removes genuine duplication or hides genuine complexity. An abstraction that requires explanation is not
  yet the right abstraction.
- **Minimal surface, maximum cohesion**: each class and method should do one thing well. If you cannot describe
  a class's responsibility in one sentence, split it.
- **Less code is usually better code**: prefer a shorter, clearer implementation. If a helper method is used
  once and adds no clarity, inline it.
- **Extensibility by design**: structure code so new behaviour is added by adding new types, not by modifying
  existing ones. Favour composition and plugin points over switch-on-type logic.
- **No accidental complexity**: do not build infrastructure for hypothetical future needs. Solve the problem
  at hand with the minimum structure required, and refactor when real new requirements arrive.
- **Performance is a first-class concern**: prefer efficient data structures and algorithms from the start;
  avoid unnecessary allocations, redundant iterations, and blocking in hot paths. Use virtual threads and
  async patterns where latency or throughput matters.

## Development Guidelines

1. **Write tests for all features**: Add or update tests for any new feature, bug fix, or behavior change.
2. Favor small, focused changes; avoid unnecessary refactors.
3. Update relevant documentation when behavior changes.
4. Add abstractions only when they clarify ownership and reduce duplication.
5. Use `final` wherever possible to emphasize immutability.
6. Prefer `static` methods for utility semantics.
7. Make an explicit choice to treat classes as singleton services or utility classes.
8. Reuse existing utility methods; extend utility classes rather than duplicating logic in private methods.
9. **Bug fix protocol**: When fixing a bug, first write a test that fails (demonstrating the bug exists), then fix the code, then verify the test passes. This ensures the bug is captured and won't regress.
10. Leverage Java 25 features (virtual threads, string templates, records) where they improve clarity or performance.
11. Place shared Gradle configuration (toolchains, Spotless, preview flags) in the conventions plugin.
12. Avoid redundant or low-value tests that do not exercise functional behavior.
13. Document REST endpoints with MicroProfile OpenAPI annotations.
14. Avoid qualified class names; add explicit imports instead.
15. Avoid methods with long argument lists; avoid side-effect methods unless the abstraction calls for them.
16. Record future improvements, deferred issues, or follow-up features in `TODO.md`.
17. Avoid needless, simple, or tautological comments; keep comments for non-obvious context. Never
    write changelog-style comments that explain what changed or why code was removed/simplified
    (e.g. "X is now unconditional, so the old check isn't needed") — that narrates the diff, not
    the resulting code; a reader with no session context gets no value from it once the change is
    old. State that reasoning in chat instead.
18. Avoid narrow, example-specific hacks; fix root causes or document follow-ups in `TODO.md`.
19. Include `UNKNOWN` enum values and a `valueOfOrDefault` parser for all enums.
20. Name `Map` fields/variables `keyVsValue`, not `valuesByKey` (e.g. `sessionVsScope` for a
    `Map<String, RunScope>` keyed by session id, `idVsFunctionCall` for a `Map<String, FunctionCall>`).
21. Order class members with all `public` methods first, then all `private` methods after —
    never interleave them, even when a private helper is only used by one nearby public method.
