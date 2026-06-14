# 3. Configuration Reference

## 3.1 Application Properties and Environment

Key runtime inputs used across modules:

- `mongodb.connection.string` / `MONGODB_CONNECTION_STRING`
- `agentengine.grpc.host` / `agentengine.grpc.port` (REST → service gRPC transport)

The default Mongo binding in this repo is `mongodb://localhost:27018`.

In Kubernetes, non-secret runtime config is mounted as an external `application.properties` file, while sensitive values come from environment variables or Secrets. Secret fields such as model `apiKey` are encrypted at rest (see [`08-deployment-and-operations.md`](./08-deployment-and-operations.md) §8.4) and should never be committed in plaintext config.

## 3.2 Agent Config (`BaseAgentConfig`)

Core fields:

- `id`
- `name`
- `type` (`default`, `orchestrator`)
- `description`
- `avatar`
- `capabilities` (`List<String>`) — optional tags used for community expert discovery
- `modelId`
- `systemPrompt`
- `contextStrategy`
- `tools` (`List<ToolsConfig>`)
- `subAgentIds`
- `guardrails`
- `runtime` (`AgentRuntimeConfig`)
- `toolExecutionMode` (`PARALLEL` default, or `SEQUENTIAL`)
- `responseFormat` — JSON Schema object the framework enforces on model output; the runtime validates the response and applies a correction loop if the schema is violated. Used by community experts such as `memory-agent` to guarantee structured output.
- `knowledgeSettings` — see §3.10

Validation rules enforced by `ConfigValidationService` + custom validators:

- `modelId` is required for default agents, unknown type, and orchestrators in `TRANSFER` (or unknown) mode
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

The strategy type is selected by the config subtype; presence of the strategy is its enablement.

### `compaction` (`CompactionContextStrategyConfig`)

- `tokenThreshold` (default `4096`)
- `recencyThreshold` (default `1024`)
- `modelId` (optional override for the compaction model)
- `promptTemplate` (optional)

### `last_n` (`LastNContextStrategyConfig`)

- `keepLastTokens` (default `1024`)

### `none`

- no fields beyond the type

## 3.5 Agent Runtime Config

`AgentRuntimeConfig`:

- `resumable` (default `true`)
- `maxSteps` (default `50`; non-positive values reset to 50)

## 3.6 Guardrails Config

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

## 3.7 Model Config (`ModelConfig`)

Model config is polymorphic, discriminated by `type`:

- `type`: `CHAT` (`ChatModelConfig`) or `EMBEDDING` (`EmbeddingModelConfig`)
- `provider`: `OLLAMA`, `OPEN_AI_COMPATIBLE`, `GEMINI`

### Base fields (all model types)

- `id`
- `name`
- `model` (the provider-side model identifier)
- `baseUrl`
- `apiKey` (encrypted at rest; provide via Secret/env, not committed config)

### `CHAT` fields (`ChatModelConfig`)

- `instructions`
- `responseFormat`
- `toolCallingEnabled` (default `false`)
- `thoughtsEnabled` (default `true`)
- `temperature`, `topK`, `topP`, `repeatPenalty`
- `numPredict` (max tokens to generate)
- `maxContextLength`

### `EMBEDDING` fields (`EmbeddingModelConfig`)

- `dimensions`
- `contextLength`

Example chat model:

```json
{
  "id": "devstral",
  "name": "Devstral",
  "type": "CHAT",
  "provider": "OPEN_AI_COMPATIBLE",
  "model": "devstral-latest",
  "baseUrl": "https://api.mistral.ai/v1",
  "apiKey": "",
  "temperature": 0.7,
  "topK": 50,
  "topP": 1,
  "toolCallingEnabled": true
}
```

## 3.8 Tool Config Entries (`ToolsConfig`)

Each configured tool entry:

- `toolName`
- `configs` (`Map<String,Object>`) passed to the tool provider during creation

`ToolCatalog` exposes visible tool metadata, and `ToolFactory` builds configured `BaseTool` and `BaseToolset` runtime instances without expanding toolsets into individual tools.

## 3.9 Infra Config (`DefaultModelsConfig`)

Stored in `INFRA.InfraConfig` under category `DEFAULT_MODELS` / id `default`:

- `titleModelId` — model used to generate session titles
- `compactionModelId` — fallback compaction model when not set on the agent's context strategy
- `evaluatorModelId` — model used by the guardrail relevance scorer
- `embeddingModelId` — embedding model for Qdrant-backed memory and knowledge search (e.g. `nomic-embed-text`)
- `chatModelId` — general-purpose chat model

## 3.10 Knowledge Settings (`knowledgeSettings`)

Optional field on `BaseAgentConfig`. Controls text-file indexing and search behavior for this agent:

- `embeddingModelId` — overrides `InfraConfig.embeddingModelId` for this agent's knowledge search
- `chatModelId` — model used for the LLM-boundary chunking stage
- `chunkingStrategy` (`List<ChunkingStrategy>`) — explicit ordered pipeline of chunking stages; each entry has a `type` plus stage-specific parameters

Available chunking stage types:

| Type | Description |
|---|---|
| `PARAGRAPH` | Split on paragraph boundaries |
| `SENTENCE` | Split on sentence boundaries |
| `TOKEN_CAP` | Hard cap at `maxTokensPerSegment` tokens |
| `LLM` | LLM-driven semantic boundary detection |
| `SEMANTIC` | Cosine-similarity sub-chunking; `similarityThreshold` configurable |
| `SIZE_MERGE` | Merge small adjacent chunks |

Example pipeline (LLM coarse + cosine refinement):

```json
"knowledgeSettings": {
  "embeddingModelId": "nomic-embed-text",
  "chunkingStrategy": [
    { "type": "PARAGRAPH" },
    { "type": "TOKEN_CAP", "maxTokensPerSegment": 1024 },
    { "type": "LLM" },
    { "type": "SEMANTIC", "similarityThreshold": 0.5 },
    { "type": "SIZE_MERGE" }
  ]
}
```

When `chunkingStrategy` is empty the runtime uses a default pipeline.

## 3.11 Config Seeding

Configs are not imported in-process; they are seeded through the deployment scripts, which upsert via the REST API:

- `k8s/scripts/seed-configs.sh` orchestrates seeding, calling:
  - `seed-infra-configs.sh` — upserts `configs/infra` into `INFRA.InfraConfig`
  - `seed-catalog-configs.sh` — upserts `configs/models` and `configs/agents` through the REST API

The `_id` of each asset is the configuration filename without its extension; this ID is used across the API.
