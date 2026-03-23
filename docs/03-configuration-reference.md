# 3. Configuration Reference

## 3.1 Application Properties and Environment

Key runtime inputs used across modules:

- `mongodb.connection.string`
- `MONGODB_CONNECTION_STRING`
- `agent.engine.bootstrap.dir` (default: `configs` in `interfaces:local`)
- `agentengine.grpc.host` / `agentengine.grpc.port` (REST -> service gRPC transport)

Default Mongo behavior in this repo commonly points to `mongodb://localhost:27018`.

In Kubernetes, non-secret runtime config is expected to come from an externally mounted `application.properties` file, while sensitive values should come from environment variables or Secrets.

## 3.2 Agent Config (`BaseAgentConfig`)

Core fields:

- `id`
- `name`
- `type` (`default`, `orchestrator`)
- `description`
- `avatar`
- `modelId`
- `systemPrompt`
- `contextStrategy`
- `tools` (`List<ToolsConfig>`)
- `subAgentIds`
- `sessionStore`
- `guardrails`
- `runtime`

Validation rules enforced by `ConfigValidationService` + custom validators:

- `modelId` is required for:
  - default agents
  - unknown type
  - orchestrator in `TRANSFER` or unknown orchestration mode
- `subAgentIds` must be empty unless `type=orchestrator`
- `SEQUENTIAL`/`PARALLEL` orchestrator modes require non-empty `subAgentIds`
- `PARALLEL + QUORUM` requires `quorum <= subAgentIds.size`

## 3.3 Orchestrator-Specific Fields

`OrchestratorAgentConfig` adds:

- `orchestrationMode`: `TRANSFER`, `SEQUENTIAL`, `PARALLEL`
- `parallel` (`OrchestratorParallelConfig`):
  - `aggregationPolicy`
  - `stoppingPolicy`
  - `quorum`

## 3.4 Context Strategy Configs

### `compaction`

- `enabled` (default `true`)
- `tokenThreshold` (default `4096`)
- `recencyThreshold` (default `1024`)
- `modelId` (optional override for compaction model)
- `promptTemplate` (optional)

### `last_n`

- `keepLastTokens` (default `1024`, clamped to minimum 1)

### `none`

- no fields beyond type

## 3.5 Agent Runtime Config

`AgentRuntimeConfig`:

- `resumable` (default `true`)
- `maxSteps` (default `50`; non-positive values reset to 50)

## 3.6 Session Store Config

Polymorphic `SessionServiceConfig`:

- `memory`
- `mongodb` (`MongoSessionServiceConfig`) with `connectionString`

Note: repository currently persists sessions via Mongo-backed service implementation.

## 3.7 Guardrails Config

Top-level:

- `enabled`
- `defaultOnError`: `FAIL_OPEN`, `FAIL_CLOSED`
- `rules`

Rule base fields:

- `id`
- `type`: `TEXT_CONTENT`, `TOOL_SAFETY`, `RELEVANCE`
- `stage`: `INPUT`, `TOOL`, `OUTPUT`
- `enabled`
- `action`: `ALLOW`, `WARN`, `BLOCK`, `ESCALATE`
- `message`

## 3.8 Model Config (`ModelConfig`)

Required:

- `type`: `ollama`, `open_ai_compatible`, `gemini`
- `model`

Common optional fields:

- `baseUrl`
- `temperature`, `topK`, `topP`, `repeatPenalty`
- `numPredict`, `maxContextLength`
- `stopTokens`
- `responseFormat`
- `instructions`
- `toolCallingEnabled`
- `apiKey`
- `serverCommand`, `serverArgs`, `serverWorkdir`

`ModelRepository` behavior for `open_ai_compatible`:

- generates server config on insert when needed
- on update/save, preserves existing server config if missing in payload

## 3.9 Tool Config Entries (`ToolsConfig`)

Each configured tool entry:

- `toolName`
- `configs` (`Map<String,Object>`) passed to tool provider during creation

`ToolCatalog` exposes visible tool metadata, and `ToolFactory` builds configured `BaseTool` and
`BaseToolset` runtime instances without expanding toolsets into individual tools.

## 3.10 Config Bootstrap

`interfaces:local` `Bootstrapper` on startup:

- resolves `agent.engine.bootstrap.dir` (searches upward if path not found)
- bootstraps infra defaults (`DefaultModelConfig`) into `INFRA`
- imports JSON files from:
  - `<bootstrapDir>/agents`
  - `<bootstrapDir>/models`
