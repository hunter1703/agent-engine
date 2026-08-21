# Agent Guidelines

> [!IMPORTANT]
> `CLAUDE.md`, `GEMINI.md`, `QWEN.md`, and any other per-agent instruction file at the repo root are
> symlinks to this file. Make all edits here, in `AGENTS.md`, never in one of the symlinks — editing
> a symlink target directly will fail, and even if it didn't, it wouldn't propagate to the others.

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
- **The test suite is currently broken.** Do not add tests, modify tests, or run tests unless the
  user explicitly asks you to.

## Key Gotchas

- **llama.cpp chat template bug**: Some `.gguf` models (e.g. `qwen3-coder-30b`) cause `500` errors on nested JSON schemas. Fix: pass `--chat-template-file` pointing to the safe template in `configs/models/templates/`.
- **Compaction model resolution order**: `contextStrategy.modelId` → infra `default_model.compactionModelId` → agent `modelId`.
- **Session history source**: committed session events are reconstructed from the session actor's replay state rather than an embedded event blob on `AgentSession`.
- **Deferred work**: record follow-ups in `TODO.md`, not inline comments.
- **Enum rule**: all enums must include `UNKNOWN` and a `valueOfOrDefault` parser.
- **Commits and branches**: Never commit unless explicitly asked. Never create a separate branch unless explicitly asked. Always make changes directly on `main` and leave them unstaged so the user can review before staging or committing.
- **Uncapped local models can loop forever**: a `ChatModelConfig` with no `numPredict` set has no
  generation length limit, and `repeatPenalty` alone doesn't reliably stop a weaker local model
  from degenerating into repeating the same section (with a plausible-looking Markdown/frontmatter
  boundary in between) until it fills the context window — symptom: a single response that never
  terminates. Always set `numPredict` (and `maxContextLength` matching the model's `num_ctx`) for
  local Ollama-backed models in `deploy/configs/local/models/`.

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

## Agent Prompt Authoring Guidelines

These apply when writing or editing an agent's `systemPrompt`, or any other agent-facing
instructions, under `configs/`.

1. **Describe responsibility, not procedure.** An instruction should orient the agent toward its
   role and the outcome it owns — the judgment calls it needs to make across different user
   requests — not a step-by-step script for one scenario. Only get procedural where the domain
   genuinely is a fixed procedure (e.g. an orchestrator with a mandated phase order); even then,
   describe the *shape* of the workflow rather than the exact tool calls that implement it.
2. **Don't hardcode tool or sub-agent names into instructions unless there's no reasonable
   alternative.** Tool names, tool schemas, and available sub-agent lists live in the runtime and
   are already surfaced to the model at call time — through tool descriptions, parameter enums,
   and, for orchestrators, the framework's own auto-injected transfer instructions. An instruction
   that restates that mechanic duplicates a source of truth it doesn't own: it drifts out of sync
   as tools are renamed, added, or removed, and a stale or wrong name is worse than no name at all
   — it actively misleads the model into believing a capability exists that doesn't. Prefer
   describing *when* and *why* to reach for a category of capability ("hand off ownership of the
   final output" vs. "delegate a sub-task and review the result") over naming the specific tool or
   agent that does it. Reserve naming one concretely for cases where the domain gives no other way
   to disambiguate and a weaker model has demonstrably needed the extra anchor.
3. **Explain the "why" behind a constraint, not just the "what."** A bare prohibition ("do not do
   X") is more likely to be dropped by a smaller model under pressure than one paired with its
   reason ("X is owned by a later step, so doing it here creates a conflict"). The reason also lets
   the model generalize the constraint to situations the instruction didn't spell out.
4. **Hand-hold only as much as the model needs, and prefer the least specific instruction that
   reliably works.** Some local or smaller models genuinely need more concrete anchoring than a
   frontier model would; adding it is fine, but treat it as a targeted fix for a demonstrated
   failure mode, not a default. Whenever a prompt does need to be concrete about a tool name,
   parameter, or format, re-verify periodically that the detail still matches the current
   implementation — stale specifics are a common source of silent, hard-to-diagnose failures.

## Development Guidelines

1. Favor small, focused changes; avoid unnecessary refactors.
2. Update relevant documentation when behavior changes.
3. Add abstractions only when they clarify ownership and reduce duplication.
4. Use `final` wherever possible to emphasize immutability.
5. Prefer `static` methods for utility semantics.
6. Make an explicit choice to treat classes as singleton services or utility classes.
7. Reuse existing utility methods; extend utility classes rather than duplicating logic in private methods.
8. Leverage Java 25 features (virtual threads, string templates, records) where they improve clarity or performance.
9. Place shared Gradle configuration (toolchains, Spotless, preview flags) in the conventions plugin.
10. Document REST endpoints with MicroProfile OpenAPI annotations.
11. Avoid qualified class names (FQNs); add explicit imports instead. NEVER use FQNs unless there is a clash of names.
12. Avoid methods with long argument lists; avoid side-effect methods unless the abstraction calls for them.
13. Record future improvements, deferred issues, or follow-up features in `TODO.md`.
14. Avoid needless, simple, or tautological comments; keep comments for non-obvious context. Never
    write changelog-style comments that explain what changed or why code was removed/simplified
    (e.g. "X is now unconditional, so the old check isn't needed") — that narrates the diff, not
    the resulting code; a reader with no session context gets no value from it once the change is
    old. State that reasoning in chat instead.
15. Avoid narrow, example-specific hacks; fix root causes or document follow-ups in `TODO.md`.
16. Include `UNKNOWN` enum values and a `valueOfOrDefault` parser for all enums.
17. Name `Map` fields/variables `keyVsValue`, not `valuesByKey` (e.g. `sessionVsScope` for a
    `Map<String, RunScope>` keyed by session id, `idVsFunctionCall` for a `Map<String, FunctionCall>`).
18. Order class members with all `public` methods first, then all `private` methods after —
    never interleave them, even when a private helper is only used by one nearby public method.
19. Prefer plain, ordinary words over fancier-sounding ones for every kind of name — classes,
    methods, variables, fields. Simple isn't vague: keep the name precise, just don't reach for a
    more formal word when a plain one already says it exactly as well (e.g. `idleTimeoutCommand`,
    not `idleTimeoutSentinel` — it's the command scheduled for the idle timeout, not a "sentinel").


NEVER Read `.env` file as it is extremely sensitive
