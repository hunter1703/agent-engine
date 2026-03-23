# 1. System Overview

## 1.1 What Agent Engine Is

Agent Engine is a modular Java 25 runtime for LLM-driven agents built around:

- **Google ADK** for agent execution and session/event primitives
- **Quarkus** for DI, lifecycle, REST, gRPC, and runtime services
- **LangChain4j model adapters** to support multiple provider types
- **MongoDB** for persisted agent/model/session/infra data

The design separates:

- **Runtime execution** (`runtime`, `runtime:actor`) from
- **Public contracts** (`runtime:api`, `core:api`) and
- **Transport/interface modules** (`interfaces:rest`).

## 1.2 Module Topology

From `settings.gradle`:

- `runtime`
- `runtime:api`
- `runtime:actor`
- `core`
- `core:api`
- `connectors:core`
- `interfaces:rest`
- `util:common`
- `util:mongodb`
- `util:ms`
- `util:agents`
- `util:pekko`

Practical meaning:

- `runtime:api` and `core:api` define service interfaces and shared contracts.
- `runtime` and `runtime:actor` implement execution behavior, model/agent builders, guardrails, tools, and session actors.
- `core` handles config CRUD, validation, and AG-UI event mapping.
- `interfaces:rest` exposes the HTTP/SSE API and maps runtime events to client event protocols.
- `connectors:core` is an independent config-driven HTTP connector framework used by tools (for example `web_lookup`).

## 1.3 Execution Model in One Pass

A single agent request follows this shape:

1. REST receives an `AgentRequest` (`/v1/agent/events`).
2. Request is handed to `AgentExecutionService`.
3. Runtime resolves agent config + session, builds/loads the agent graph, model, and configured toolsets.
4. Google ADK runner emits stream of `Event`s.
5. REST mapper converts ADK events to AG-UI SSE events.
6. Session state/history persists via `AgentSessionRepository`.

## 1.4 Primary Runtime Capabilities

- Default and orchestrator agents
- Orchestrator modes: `TRANSFER`, `SEQUENTIAL`, `PARALLEL`
- Context strategies: `compaction`, `last_n`, `none`
- Guardrail stages: `INPUT`, `TOOL`, `OUTPUT`
- Tool registry with globally visible tools and suite expansion
- Session pause/resume for confirmation/HITL-style flows
- Built-in toolsets such as `planning` and `agent_tools`

## 1.5 Data Domains

MongoDB databases/collections used by default:

- `AGENT_ENGINE.Agent` (agent configs)
- `AGENT_ENGINE.Model` (model configs)
- `AGENT_ENGINE.AgentSession` (sessions + serialized event history)
- `INFRA.InfraConfig` (runtime infra configs like encryption/default model)

## 1.6 Why The API Modules Matter

`runtime:api` and `core:api` provide the contract layers for:

- transport decoupling
- local-vs-remote service dispatch (`@MicroService` + gRPC proxy fallback)
- typed configuration and shared query/update models

If you integrate new interfaces (beyond REST), this module is the first dependency to target.
