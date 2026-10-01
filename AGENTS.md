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

# Deploy the standard Kubernetes stack (run from deploy/scripts/, a uv-managed Python CLI)
uv run deployae deploy

# Tear down the standard Kubernetes stack (run from deploy/scripts/)
uv run deployae cleanup

# Build a service image (module: agent/core, catalog/core, tenancy/core, interfaces/rest, knowledge/core,
# connectors/core, scheduler/core, or internal)
docker build --build-arg SERVICE_MODULE=agent/core -f deploy/docker/Dockerfile .
```

## Module Structure

| Module                                                     | Purpose                                                                                                     |
|--------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `agent:api`, `agent:core`, `agent:infra`, `agent:jobs`      | **Agent service** — agent construction, model providers, tools, guardrails, orchestration, sessions, memory. agent schedules. `agent:jobs` holds scheduled-job definitions fired by the scheduler service |
| `catalog:api`, `catalog:core`                                | **Catalog service** — config CRUD/validation, asset catalog, schema contracts, AG-UI event mapping        |
| `knowledge:api`, `knowledge:core`                             | **Knowledge service** — document indexing and semantic search over Qdrant                                  |
| `identity`                                                    | Login and sessions, used by the REST service                                                              |
| `interfaces:rest`                                             | **REST service** — user-facing HTTP/SSE gateway (port 8080)                                                |
| `tenancy:api`, `tenancy:core`, `tenancy:jobs`                 | **Tenancy service** — customers, roles and role mappings, sharing. `api` also holds what every permissioned service uses: the `PermissionChecker` implementation, and `AccessControlService`, the tenancy client for access lists and for submitting stale role and role mapping tasks again. `tenancy:jobs` holds the per-customer jobs, fired by the scheduler service, that have stale role and role mapping tasks submitted again |
| `connectors:api`, `connectors:core`, `connectors:http`, `connectors:infra` | **Connectors service** — config-driven HTTP connector framework (templating, auth, pagination, retry) backing tools such as `web_research` |
| `scheduler:api`, `scheduler:core`                             | **Scheduler service** — cron-style job scheduling; fires jobs such as `agent:jobs`' `InvokeAgentJob`       |
| `internal`                                                    | **Internal service** — internal/ops REST endpoints (Mongo, scheduler introspection)                        |
| `chaos:api`, `chaos:core`                                     | Fault-injection / chaos-experiment framework. Not a deployed service — run standalone against the stack   |
| `util:common`                                                 | Cross-module utility classes (queries, updates, exceptions, collections), entity annotations (`@Index`, `@Indexed`, `@Permissioned`), and the repository contracts (`Repository`, `EntityStore`, `DocumentBackend`)                                    |
| `util:context`                                                | Request context: `Context`, `Caller`, `Principal`, context-propagating executors, `@ContextAware` binding |
| `util:tenancy`                                                | Access-control model: `Permission`, the `PermissionChecker`, `PermissionedRepository` and `AclService` contracts, `SharingChange`, `AbstractPermissionedRepository`, and `PermissionedCache` |
| `util:tasks`                                                  | Task processing contract, independent of how it is run: `Task`/`TaskStatus`, and a `TaskService` per task type that takes its tasks in, handles them and marks them done |
| `util:infra`                                                  | Infra config model and lookup — `InfraConfig`, client/server configs, `InfraConfigService` — and the provisioning contract (`ProvisioningService`/`Request`/`Result`) |
| `util:mongodb`                                                | MongoDB client factory, codec registry, infra config store, `DocumentBackend` — the document collections repositories register and read and write through — and `MongoBackend`, its MongoDB implementation |
| `util:crypto`                                                 | `EncryptionService`, versioned key configs, KMS key loading                                                 |
| `util:sql`                                                    | SQL server/client infra configs                                                                             |
| `util:models`                                                | Chat and embedding model factories, model provider cache, LLM wrappers and the text tool-call parser      |
| `util:vectordb`                                               | `VectorBackend` — the vector collections repositories register and read and write through — and `QdrantBackend`, its Qdrant implementation |
| `util:cloudstorage`                                           | Cloud object storage client                                                                                 |
| `util:ms:client`, `util:ms:server`                            | gRPC microservice transport (client dispatch, server wiring)                                                |
| `util:agents`                                                 | Agent/session domain beans and repositories shared across services                                          |
| `util:pekko`                                                  | Pekko actor-system, cluster-sharding, and persistence support, and `AbstractActorTaskService`, the base `TaskService` that submits to one coalescing actor per customer, task type and partition |
| `util:scripts`                                                | Templating utilities used by connectors and scripted config                                                 |
| `configs/`                                                    | Agent and model registry JSON/YAML definitions                                                              |
| `deploy/docker/`                                              | Shared container image build artifacts                                                                      |
| `deploy/k8s/`                                                 | Helm charts for the Kubernetes deployment                                                                   |
| `deploy/scripts/`                                             | `deployae` — the Python CLI that drives the Kubernetes deploy/cleanup workflow                              |

## Testing Conventions

- Unit tests: `src/test/java`, class `<ClassName>Test`, method `should<Behavior>When<Condition>`
- Integration tests: `src/integrationTest/java`, class `<Feature>IT`; use `@QuarkusTest` with container-backed resources
- Use mocks/fakes for pure logic; use real containers (Testcontainers) when runtime wiring matters
- **The test suite is currently broken.** Do not add tests, modify tests, or run tests unless the
  user explicitly asks you to.

## Key Gotchas

- **llama.cpp chat template bug**: Some `.gguf` models (e.g. `qwen3-coder-30b`) cause `500` errors on nested JSON schemas. Fix: pass `--chat-template-file` pointing to the safe template in `configs/models/templates/`.
- **Compaction model resolution order**: `contextStrategy.modelId` → the customer's `DefaultModels.compactionModelId` → agent `modelId`.
- **Session history source**: committed session events live in a dedicated Mongo collection
  (`SessionEventsRepository`), written once per turn at commit time and read back from there by
  most history paths (memory, title generation, the REST history API). Postgres is still the
  backing store for the Pekko actor journal itself (event-sourcing facts, via JDBC) — the ADK
  runner's in-memory session rebuild (`RunnerFactory`) is the one path that reads that journal
  directly, to fold in turn/rollback bookkeeping before fetching event bodies from Mongo.
- **Uncapped local models can loop forever**: a `ChatModelConfig` with no `numPredict` set has no
  generation length limit, and `repeatPenalty` alone doesn't reliably stop a weaker local model
  from degenerating into repeating the same section (with a plausible-looking Markdown/frontmatter
  boundary in between) until it fills the context window — symptom: a single response that never
  terminates. Always set `numPredict` (and `maxContextLength` matching the model's `num_ctx`) for
  local Ollama-backed models in `deploy/configs/local/models/`.
- **Access control model**: a request's `Context` carries its customer and its `Caller` — the
  system, nobody (before logging in), or a user (`UserCaller`). A principal (`Principal`) is a user
  acting in the context of a chain of assets, each acting in the context of the one before it —
  not a chain of ownership. It is written as `:`-joined `Class/id` segments, the user first:
  `User/5` is user 5 acting directly, `User/5:Agent/A:AgentSession/S` user 5 acting in session S of
  agent A. Any id may be `*`, so a principal named in a grant can stand for many: `User/*:Agent/A`
  is agent A for any user. A grant to a principal reaches every principal it covers — one it
  is a prefix of, with any ids widened to `*` — so what is granted to user 5 reaches everything
  acting for user 5 (what an agent can actually do is bounded by its tools), and what is granted to
  agent A everything acting within it. A `UserCaller` acts in one or more principals of one user:
  its primary principal is where the work runs and what it is attributed to; the runtime adds
  others, e.g. an agent transferred to inside a session also acts in its own `User/5:Agent/B`,
  keeping what the session reaches and adding what was shared with it. A session's turn always
  runs as the session for the user who sent the turn's message, so users sharing a session each
  reach only what was granted for them. `READ` on an agent means being allowed to use it:
  starting or resuming a session with it, or listing it as a sub-agent of another agent, which
  lets whoever runs that one use it through it. Runners are built as the system; a sub-agent is not checked again when it is
  spawned or transferred to, since listing it was. A new entity is owned by the principal
  creating it — what a user creates directly is theirs. What is created within a session for
  everyone in it (notebooks, knowledge from attachments) is owned by the session for any
  user (`Principal.forAnyUser()`), so users sharing a session share it; a memory is owned by its
  agent for its user (`User/5:Agent/A`), so the agent recalls it in that user's later sessions. A
  session hands notebooks and knowledge to another for every user of the other session. Access to an entity
  is the grants (`<principal>#<permission>`) on it plus roles on every asset of its class; a
  repository may widen it for its own entities.
  Parts of an entity with no access of their own (session events, knowledge chunks, notes) are not
  permissioned: they sit in plain repositories reached only through their owner, which checks the
  owner's access and deletes them along with it. Grants are calculated by tenancy
  from role mappings and are never written by entity saves, except that a new entity is shared
  before it is stored and stored with the access list tenancy calculated for that sharing, so it is
  reachable as soon as it exists. A sharing change marks the changed
  role mappings pending (a `util:tasks` task per asset) and tenancy's actor for that asset recalculates
  its whole access list, storing it only while the asset still holds the version it read;
  editing a role marks every mapping that uses it pending. A `tenancy:jobs` job per customer, scheduled
  when it is provisioned, has tenancy submit again whatever stays pending too long. Repositories of permissioned
  entities are `PermissionedRepository`s, built on `AbstractPermissionedRepository`, every other one extends `AbstractRepository`; both store
  through a collection registered with a `DocumentBackend` or a `VectorBackend`. Permissioned entities live
  in their customer's own database (`DocumentRepositorySpec.perCustomer`), never in a global
  collection: roles on every asset skip per-entity filtering, so only the database keeps customers
  apart. A customer-facing view of global data is its own per-customer entity that the owning
  service mirrors into the global one (e.g. `AgentSchedule` and the scheduler's `JobDefinition`).
- **Store registration happens when a repository is created**: `DocumentBackend`/`VectorBackend` set up
  (indexes, vector collections) only the collections registered with them, so every repository
  bean is `@Startup` and registers at boot. A repository built with `new` (e.g.
  `SequenceRepository`) is set up only if a repository bean's constructor creates it.

## Access-Control Glossary

Use these terms, and only these, for these ideas — in code, comments and docs:

- **Principal** (`Principal`): a user acting in the context of a chain of assets, written
  `User/5:Agent/A:AgentSession/S`; the first segment is always the user. Any id may be `*`, making
  it stand for many, e.g. `User/*:Agent/A` — agent A, for any user.
- **Covering principal**: a principal that is a prefix of another, with any ids widened to `*`; it
  covers that other one. A grant to a principal reaches every principal it covers.
- **Caller** (`Caller`): who a request acts as — the system, nobody, or a user (`UserCaller`).
- **Primary principal**: the principal a `UserCaller` runs in and attributes work to; its other
  principals, added by the runtime (e.g. a transferred agent), add access, never attribution.
- **Context principals**: every covering principal of every principal a request's caller acts in —
  what access is checked against.
- **Permission** (`Permission`): an action on an asset — `READ`, `EDIT`, `DELETE`, `CREATE`. On
  an agent, `READ` means being allowed to use it.
- **Grant**: one principal holding one permission, written `<principal>#<permission>`, e.g.
  `User/*:Agent/A#READ`. Never "grant key" or "token".
- **Access list** (`Acl`): an entity's grants and the version they were calculated at, stored on it
  as `acl`; calculated by tenancy, never written by an entity save other than its creation. "ACL" only in type and method names.
- **Role** (`Role`): a named set of permissions per asset class, e.g. `reader`, `owner`.
- **Role mapping** (`RoleMapping`): a principal holding roles on one asset, or on every asset of
  a class. Grants are calculated from role mappings.
- **Sharing** (`SharingChange`, `AclService.updateSharing`): adding or removing role mappings.
- **Role on every asset**: a role mapping on no single asset, applying to every asset of a class
  within the customer.

## Code Quality Philosophy

Write code that is **beautiful, easy to read, and architecturally elegant** — without sacrificing performance.
Aim for the solution that is simultaneously the simplest, the clearest, and the most efficient. Design for
the reader and the runtime equally.

- **Clarity over cleverness**: code should reveal its intent immediately; a reader unfamiliar with the method
  should understand what it does and why.
- **Earn every abstraction**: introduce an abstraction only when it has a clear name, a single responsibility,
  and clarifies ownership — and only when it removes genuine duplication or hides genuine complexity. An
  abstraction that requires explanation is not yet the right abstraction.
- **Don't let the bug that motivated a fix narrow its shape**: when a specific failure prompts a new method
  or abstraction, check whether the general version costs nothing extra over the narrow one — if it doesn't,
  build the general version now rather than the narrow one now and a rename later. E.g. a missing reminder
  was only ever noticed for a *created/owned* notebook; the right fix is not a method named for that one case
  (`addOwnedNotebookReminder`) but a generic one (`addNotebookReminder(id, message)`) that the owned case is
  just one caller of, since nothing about the generic shape is harder to write. This is not license to build
  speculative infrastructure (see "No accidental complexity" below) — only to not artificially narrow an
  abstraction's design, nomenclature and, scope to match the reproducing case when the general form is equally simple.
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

### Process & Workflow

1. Favor small, focused changes; avoid unnecessary refactors.
2. Update relevant documentation when behavior changes.
3. Record future improvements, deferred issues, or follow-up features in `TODO.md`, not inline comments.
4. Avoid narrow, example-specific hacks; fix root causes or document follow-ups in `TODO.md`.
   Before introducing any hack — overloading one mechanism to serve a different purpose (e.g. a
   reserved/magic key, a sentinel value, special-casing that leaks into every caller) because the
   clean abstraction doesn't exist yet — stop and confirm the approach with the user first rather
   than implementing it unilaterally. Present the tradeoff and let them choose, even if that means
   a bigger change than the hack would have been.
5. **Commits and branches**: Never commit unless explicitly asked. Never create a separate branch
   unless explicitly asked. Always make changes directly on `main` and leave them unstaged so the
   user can review before staging or committing.
6. NEVER read the `.env` file — it is extremely sensitive.

### Naming Conventions

1. Name `Map` fields/variables `keyVsValue`, not `valuesByKey` (e.g. `sessionVsScope` for a
   `Map<String, RunScope>` keyed by session id, `idVsFunctionCall` for a `Map<String, FunctionCall>`).
2. Prefer plain, ordinary words over fancier-sounding ones for every kind of name — classes,
   methods, variables, fields. Simple isn't vague: keep the name precise, just don't reach for a
   more formal word when a plain one already says it exactly as well (e.g. `idleTimeoutCommand`,
   not `idleTimeoutSentinel` — it's the command scheduled for the idle timeout, not a "sentinel").
   Applies to booleans too: `sameClass`, not `homogeneous` — it's a plain description of the check
   ("do all these items share one class"), not a term of art the reader has to already know.
3. Don't squeeze a name into one short word. A name must say precisely what the thing does in
   its context — `hasNotePermission(note, notebook, permission)`, not `can(...)`;
   `isSharedWithSession(entity)`, not `isShared(...)`; `loadCustomerNotebooks()`, not `loadAll()` —
   without turning formal or long-winded. Private helpers are no exception.
4. Name a method that gets one thing by its id `get`, not `find`, `fetch` or `lookup` — e.g.
   `InfraConfigService.get(id)`. Reserve `find` for searches that match by a query or condition
   and may return several results.

### Code Structure Conventions

1. Avoid methods with long argument lists; avoid side-effect-only methods unless necessary. A
   method that only mutates a collection/object passed in by the caller (e.g. `void
   appendFooParts(List<Part> parts, ...)`) should, when nothing about the abstraction truly
   requires the side effect, instead be a pure function that returns the new/changed value (e.g.
   `List<Part> fooParts(...)`) for the caller to assign or add — this is easier to read, test, and
   reason about than a method whose effect is only visible by inspecting a parameter after the
   call. Only reach for a side-effect method when the mutation is the point of the abstraction
   (e.g. a builder, a `Map` accumulator threaded through a loop where allocating a fresh
   collection per call would be wasteful).
2. Order class members with all `public` methods first, then all `private` methods after —
   never interleave them, even when a private helper is only used by one nearby public method.
3. Order instance fields (and matching constructor parameters/getters/setters) by conceptual
   importance or ownership, most fundamental first — not alphabetically or by whenever they were
   added. A field that another field belongs to or depends on comes before it (e.g. on
   `TurnCommittedFact`, `runId` before `turnId`, since a turn belongs to a run). When adding a new
   field to an existing class, insert it at its rightful position in that hierarchy rather than
   appending it at the end.

### Request Context

1. Never override the request `Context` — `as(...)`, `actingAs(...)`, `alsoActingAs(...)`,
   `asSystem(...)`, or binding a different context — unless the code genuinely has to act
   as someone other than its caller. Work runs in the context it was called in, so checks and
   attribution follow whoever triggered it; an override that isn't needed silently widens or hides
   who acted. Legitimate overrides are few: work no caller has a context for (e.g. a job firing, a
   cache load shared by every caller), a read of data the caller is not meant to reach directly
   but the work needs (e.g. a session's parent record), and re-entering a context that was stored
   because the chain carrying it broke (e.g. starting a queued run). A shortcut to skip a
   permission check is not one: make the check cheap instead.
2. A service runs in its caller's context. When a call has to be made as the system, the caller
   switches before calling; the service never switches on its own behalf.

### Writes

1. Version every update. A write of an entity that was read first goes through only while the
   stored entity is still at the version that was read, and fails otherwise
   (`StaleStateException`), so a concurrent write is never silently overwritten. Skip the version
   only for a non-negotiable reason, and say why at the call site.
2. A versioned write takes the plain name (`save`, `update`, `delete`); a write that skips the
   version says so in its name (`saveIgnoringVersion`, `updateManyIgnoringVersion`,
   `deleteByIdIgnoringVersion`).

### Java Style & Idioms

1. Use `final` wherever possible to emphasize immutability.
2. Prefer `static` methods for utility semantics.
3. Make an explicit choice to treat classes as singleton services or utility classes.
4. Reuse existing utility methods; extend utility classes rather than duplicating logic in private methods.
5. Leverage Java 25 features (virtual threads, string templates, records) where they improve clarity or performance.
6. Avoid qualified class names (FQNs); add explicit imports instead. NEVER use FQNs unless there is a clash of names.
7. NEVER use `var`. Always declare the actual type, including for local variables, loop
   variables, and record deconstruction patterns.
8. Include `UNKNOWN` enum values and a `valueOfOrDefault` parser for all enums.
9. Never type a field of a serialized or deserialized bean (Mongo documents, config beans, JSON DTOs)
   as an enum. Keep it a `String` and parse it with the enum's parser (rule 8) where it is used:
   an enum field fails to load the moment a stored or received value is not in the current list —
   after a value is removed or renamed, or written by a newer version — where a `String` degrades
   to `UNKNOWN` and the code handles it.
10. Place shared Gradle configuration (toolchains, Spotless, preview flags) in the conventions plugin.
11. Document REST endpoints with MicroProfile OpenAPI annotations.

### Comment Philosophy

Avoid needless, simple, or tautological comments; keep comments for non-obvious context. NEVER
treat a comment as a log of development — it must document the code's current, standalone
behavior for a reader who has no idea what changed, not narrate the change itself. This rules
out changelog-style comments about what changed or why code was removed/simplified (e.g. "X is
now unconditional, so the old check isn't needed"), and it equally rules out a comment that
justifies an absence by contrasting it with history the reader never saw (e.g. "Pure
construction, no validation — a caller must run validate() itself" reads as answering "didn't
this used to validate?", a question only someone who watched it change would think to ask). A
reader meeting the code for the first time isn't surprised by what it doesn't do unless the
comment itself plants that expectation — describe what the method does and, if genuinely
non-obvious, what its caller is responsible for, without referencing a prior state. Development
reasoning belongs in chat or the PR description, never in the comment, and stops being relevant
to anyone the moment the change is no longer new. A comment must also never give the reader a
usage tip or recommendation (e.g. "use this for reads that must never be silently truncated") —
that is advice about when to reach for the code, not a description of what the code is or does,
and it's the wrong direction: the shared utility shouldn't be prescribing to its callers when a
caller hasn't been written yet. State the fact instead (e.g. "Represents an unbounded read —
offset 0, limit <= 0") and let each call site's own comment, if one is even needed, explain why
that call site chose it.

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
