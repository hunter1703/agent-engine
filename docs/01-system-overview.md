# 1. System Overview

## 1.1 What Agent Engine Is

Agent Engine is a modular Java 25 runtime for LLM-driven agents built around:

- **Google ADK** for agent execution and session/event primitives
- **Quarkus** for DI, lifecycle, REST, gRPC, and runtime services
- **LangChain4j model adapters** to support multiple provider types
- **MongoDB** for persisted agent/model/session/infra data

The design separates:

- **Core runtime** (`engine`) from
- **Public contracts** (`engine:api`) and
- **Transport/interface modules** (`interfaces:rest`, `interfaces:local`).

## 1.2 Module Topology

From `settings.gradle`:

- `engine`
- `engine:api`
- `connectors`
- `connectors:core`
- `interfaces`
- `interfaces:rest`
- `interfaces:local`

Practical meaning:

- `engine:api` defines service interfaces, config beans, tool contracts, query/update DSL, and gRPC schema.
- `engine` implements runtime behavior, repositories, model/agent builders, plugins, guardrails, and built-in tools.
- `interfaces:rest` exposes the HTTP/SSE API and maps engine events to client event protocols.
- `interfaces:local` bootstraps initial model/agent data into Mongo at startup.
- `connectors:core` is an independent config-driven HTTP connector framework used by tools (for example `web_lookup`).

## 1.3 Execution Model in One Pass

A single agent request follows this shape:

1. REST receives an `AgentRequest` (`/v1/agent/events`).
2. Request is handed to `AgentExecutionService`.
3. Runtime resolves agent config + session, builds/loads agent graph, model, tools, and plugins.
4. Google ADK runner emits stream of `Event`s.
5. REST mapper converts ADK events to AG-UI SSE events.
6. Session state/history persists via `AgentSessionRepository`.

## 1.4 Primary Runtime Capabilities

- Default and orchestrator agents
- Orchestrator modes: `TRANSFER`, `MANAGER`, `SEQUENTIAL`, `PARALLEL`
- Context strategies: `compaction`, `last_n`, `none`
- Guardrail stages: `INPUT`, `TOOL`, `OUTPUT`
- Tool registry with globally visible tools and suite expansion
- Session pause/resume for confirmation/HITL-style flows
- Optional plugin loading from external JARs (`PLUGIN_DIR`)

## 1.5 Data Domains

MongoDB databases/collections used by default:

- `AGENT_ENGINE.Agent` (agent configs)
- `AGENT_ENGINE.Model` (model configs)
- `AGENT_ENGINE.AgentSession` (sessions + serialized event history)
- `INFRA.InfraConfig` (runtime infra configs like encryption/default model)

## 1.6 Why `engine:api` Matters

`engine:api` is the contract layer for:

- transport decoupling
- local-vs-remote service dispatch (`@MicroService` + gRPC proxy fallback)
- typed configuration and shared query/update models

If you integrate new interfaces (beyond REST), this module is the first dependency to target.
