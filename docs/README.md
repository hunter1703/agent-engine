# Agent Engine Documentation

This is the in-depth documentation set for Agent Engine. For a high-level introduction, the architecture overview, and the quick start, see the [root README](../README.md). The documents here go deeper into each subsystem and track the current source.

## Document Map

1. [`01-system-overview.md`](./01-system-overview.md) — modules, execution model, and data domains
2. [`02-runtime-architecture.md`](./02-runtime-architecture.md) — services, session actors, orchestration, memory, rollback
3. [`03-configuration-reference.md`](./03-configuration-reference.md) — agent, model, guardrail, and knowledge configuration
4. [`04-rest-and-grpc-apis.md`](./04-rest-and-grpc-apis.md) — REST endpoints and gRPC transport
5. [`05-tooling-and-plugins.md`](./05-tooling-and-plugins.md) — tool contract and extension points
6. [`06-connectors-framework.md`](./06-connectors-framework.md) — config-driven HTTP connector engine
7. [`07-development-and-testing.md`](./07-development-and-testing.md) — toolchain, build, and test conventions
8. [`08-deployment-and-operations.md`](./08-deployment-and-operations.md) — Kubernetes deployment and operations
9. [`09-practical-recipes.md`](./09-practical-recipes.md) — end-to-end usage examples
10. [`10-protocol-and-guarantees.md`](./10-protocol-and-guarantees.md) — normative runtime protocol
11. [`community-model.md`](./community-model.md) — expert discovery and invocation

## Baseline

- **Repository root:** `agent-engine`
- **Language/toolchain:** Java 25 (preview features enabled)
- **Frameworks:** Quarkus, Google ADK (agent execution), LangChain4j (model adapters), Apache Pekko (cluster sharding and event sourcing)
- **Persistence:** MongoDB (configs, sessions, infra) and Qdrant (memory and knowledge vectors); the default local Mongo binding is `mongodb://localhost:27018`

## Module Topology

Gradle modules, from `settings.gradle`:

| Module | Role |
|---|---|
| `agent`, `agent:api`, `agent:core`, `agent:infra` | Execution runtime: agent construction, model providers, tools, guardrails, orchestration, session actors, memory |
| `catalog`, `catalog:api` | Config CRUD and validation, asset catalog, schema contracts, AG-UI event mapping |
| `knowledge`, `knowledge:api`, `knowledge:core` | Text-file indexing and semantic search over Qdrant |
| `connectors:core` | Config-driven HTTP connector framework |
| `interfaces:rest` | REST/SSE gateway — the only wired interface module (`interfaces:local` exists on disk but is not included in `settings.gradle`) |
| `util:*` | Shared utilities: `common`, `mongodb`, `vectordb`, `cloudstorage`, `ms` and `ms:client` (microservice transport), `agents` (shared beans), `pekko` |

The four deployable services are `agent`, `catalog`, `knowledge`, and `rest`; they communicate over the internal gRPC transport and share MongoDB and Qdrant.
