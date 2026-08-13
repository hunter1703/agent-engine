# 1. System Overview

## 1.1 What Agent Engine Is

Agent Engine is a modular Java 25 runtime for LLM-driven agents built around:

- **Google ADK** for agent execution and session/event primitives
- **Quarkus** for DI, lifecycle, REST, gRPC, and runtime services
- **LangChain4j model adapters** to support multiple provider types
- **Apache Pekko** for cluster-sharded, event-sourced session actors
- **MongoDB** for persisted agent/model/session/infra data, and **Qdrant** for memory and knowledge vectors

The design separates:

- **Execution runtime** (`agent`, `agent:core`, `agent:infra`) from
- **Public contracts** (`agent:api`, `catalog:api`, `knowledge:api`) and
- **Transport/interface modules** (`interfaces:rest`).

## 1.2 Module Topology

From `settings.gradle`:

- `agent`, `agent:api`, `agent:core`, `agent:infra`
- `catalog`, `catalog:api`
- `knowledge`, `knowledge:api`, `knowledge:core`
- `connectors:core`
- `interfaces:rest`
- `util:common`, `util:mongodb`, `util:vectordb`, `util:cloudstorage`, `util:ms`, `util:ms:client`, `util:agents`, `util:pekko`

Practical meaning:

- `agent:api`, `catalog:api`, and `knowledge:api` define service interfaces and shared contracts.
- `agent`, `agent:core`, and `agent:infra` implement execution behavior, model/agent builders, guardrails, tools, and session actors.
- `catalog` handles config CRUD, validation, the asset catalog, schema contracts, and AG-UI event mapping.
- `interfaces:rest` exposes the HTTP/SSE API and maps runtime events to client event protocols.
- `connectors:core` is an independent config-driven HTTP connector framework used by tools (for example `web_research`).
- `knowledge` implements the text-file indexing and semantic search pipeline backed by Qdrant.

The four deployable services are `agent`, `catalog`, `knowledge`, and `rest`. Only `interfaces:rest` is wired as an interface module; `interfaces:local` exists on disk but is not included in `settings.gradle`.

## 1.3 Execution Model in One Pass

A single agent request follows this shape:

1. REST receives an AG-UI `RunAgentInput` at `POST /v1/agent/{agentId}/invoke`.
2. The request is dispatched to the Agent service's `RuntimeService` (local bean or gRPC proxy).
3. `RuntimeService.startSession` routes to the session's `SessionActor` — a cluster-sharded, event-sourced entity that owns session state.
4. The actor builds a `SessionRunner` via `RunnerFactory`, which constructs the Google ADK `Runner` with the resolved agent graph, model, and toolsets.
5. The ADK runner emits a stream of `Event`s; `AGUIEventMapper` converts them to AG-UI SSE events streamed back through REST.
6. Session state and event history persist through the actor's event-sourced journal.

## 1.4 Primary Runtime Capabilities

- Default and orchestrator agents
- Orchestrator modes: `TRANSFER`, `SEQUENTIAL`, `PARALLEL`
- Context strategies: `compaction`, `last_n`, `none`
- Guardrail stages: `INPUT`, `TOOL`, `OUTPUT`
- Tool registry with globally visible tools and toolset suites
- Session pause/resume for interrupt-driven and human-in-the-loop flows
- Session rollback to a prior run state
- Built-in toolsets such as `planning` and `agent_tools`
- Persistent cross-session memory backed by Qdrant (extracted by the `memory-agent` community expert)
- Text-file knowledge indexing and semantic search via Qdrant

## 1.5 Data Domains

MongoDB databases/collections used by default:

- `AGENT_ENGINE.Agent` (agent configs)
- `AGENT_ENGINE.Model` (model configs)
- `AGENT_ENGINE.AgentSession` (sessions + serialized event history)
- `INFRA.InfraConfig` (runtime infra configs such as encryption key and default models)

Qdrant collections (vector store):

- `Memory` (persistent per-user, per-agent memories; text field embedded for semantic retrieval)
- `KnowledgeChunk` (indexed chunks of text files uploaded during sessions; searchable via `search_knowledge`)

Qdrant is required for memory and knowledge features. Both collections use the embedding model configured in `InfraConfig.embeddingModelId` (or an agent-level override in `knowledgeSettings`).

## 1.6 Why The API Modules Matter

`agent:api`, `catalog:api`, and `knowledge:api` provide the contract layers for:

- transport decoupling
- local-vs-remote service dispatch (`@MicroService` + gRPC proxy fallback)
- typed configuration and shared query/update models

If you integrate new interfaces beyond REST, these contract modules are the first dependency to target.
