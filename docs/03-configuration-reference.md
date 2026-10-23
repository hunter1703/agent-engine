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

Validation rules enforced by `ConfigValidationService` + custom validators:

- `modelId` is required for `default` agents and for orchestrators in `MANAGER` (or unknown) mode; an `image_classification` agent needs `modelId` or `subAgentIds`
- `subAgentIds` must be empty unless `type=orchestrator` or `type=image_classification`
- `SEQUENTIAL`/`PARALLEL` orchestrator modes require non-empty `subAgentIds`
- `PARALLEL + QUORUM` requires `quorum <= subAgentIds.size`

## 3.3 Orchestrator-Specific Fields

`OrchestratorAgentConfig` adds:

- `orchestrationMode`: `TRANSFER`, `SEQUENTIAL`, `PARALLEL`
- `parallel` (`OrchestratorParallelConfig`):
  - `aggregationPolicy`
  - `stoppingPolicy`
  - `quorum`

### Image classification agents (`IMAGE_CLASSIFICATION`)

An `IMAGE_CLASSIFICATION` agent tells which tags of the indexed knowledge the attached image belongs with. It runs no model besides the embedding model named by `modelId`, and it only counts knowledge indexed with that model (see `modalityVsEmbeddingModelIds` in §3.10). Its answer is `{"labels": [{"label", "probability", "score"}, ...]}`, most probable first.

- `closestChunksPerTag`: how many of a tag's closest indexed images its similarity is the mean of (default 3)
- `matchThreshold`: the similarity an indexed image needs to the input to count towards a tag
- `sharpness`: how fast a tag's probability rises with its similarity above `matchThreshold`

For each vector found in the input, the store groups the indexed images at least `matchThreshold` similar by tag, and a tag's similarity is the mean of its `closestChunksPerTag` closest. A tag's `probability` is a logistic function of that similarity, and its `score` is the similarity. A tag listed has a probability of at least one half.

`subAgentIds` are other image classification agents, each comparing by its own embedding model. The agent asks them at the same time as it classifies with its own model, which it may leave out (no `modelId`), and merges the answers: a tag matches with the probability that at least one of them is right.

## 3.4 Context Strategy Configs

The strategy type is selected by the config subtype. An agent without one uses `DEFAULT` with
its defaults. Once the context passes the trigger threshold, the latest events that fit the recency
threshold are kept as they are and every older event no summary covers yet is replaced by a
summary. An event is summarized at most once and a summary is never summarized again, so the
summaries stay in the context beside the events they do not cover. Each is stored as a compaction
event in the session, so it is never recomputed on resume or restart.

The context size is the prompt of the latest model response, that response, and what came after
it; right after a compaction it is estimated from the summaries and the events no summary covers.

The summary is asked from the model by sending it the agent's last model request again with one
more message, an instruction to summarize, when the compaction model is the agent's own model. A
provider that caches the start of a prompt then has all of it cached, and the summary may mention the
latest events, which are kept as well. A different compaction model is sent the messages and the
instruction alone and reads them in full. The compaction is made right before the history of a
model request is built, and its event is stored and shown to no user. A run's first model call is
never compacted, since there is no earlier request to summarize.

A tool call is never summarized without its result: when the result lies in the latest events, the
summary reaches on to include it, so the events kept can be fewer than the recency threshold allows.
A call still waiting for its result (a long-running or asynchronous tool) is kept, with everything
after it, even beyond the recency threshold, so the result lands next to its call when it arrives.
Summaries are never made smaller, so an agent whose summaries alone pass the trigger threshold
cannot be compacted further.

### `DEFAULT` (`CompactionContextStrategyConfig`)

Replaces older events with a model-written summary, sent as a user message.

- `thresholdUnit` (default `PERCENT`): `PERCENT` measures both thresholds as a percentage of the
  agent model's `maxContextLength`; `TOKENS` measures both in tokens
- `triggerThreshold` (default `95`): the context size past which the history is compacted
- `recencyThreshold` (default `10`): the most the latest events may take after a compaction; it
  must be above 0 and below `triggerThreshold`, which is at most 100 for `PERCENT`. A model needs
  headroom for its reply and for the next tool result, so a model with a long maximum output is
  better set below the default (e.g. `80`)
- `modelId` (optional override for the compaction model)
- `promptTemplate` (optional; the instruction to summarize, with `{conversation_history}` standing
  for the conversation above)

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
- `provider`: `OPEN_AI`, `Z_AI`, `GEMINI`. `OPEN_AI` is the default for any provider that follows the OpenAI API as is (OpenAI, Groq, Mistral, NVIDIA, LLM7, Ollama, llama.cpp and so on). `Z_AI` and `GEMINI` each have a provider of their own because they differ: Z.ai has a `thinking` field and accepts only `tool_choice: auto`, and Gemini has its own client. A server reached through `OPEN_AI` needs a base URL ending in `/v1`.

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
- `maxOutputTokens` (max tokens to generate)
- `maxContextLength`

### `EMBEDDING` fields (`EmbeddingModelConfig`)

- `dimensions`
- `contextLength`
- `maxBatchSize` (default: `32` when omitted; maximum chunks sent in a single batched embedding request)

Example chat model:

```json
{
  "id": "devstral",
  "name": "Devstral",
  "type": "CHAT",
  "provider": "OPEN_AI",
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

## 3.9 Default Models (`DefaultModels`)

Configured per customer in `DefaultModels`:

- `smartModelId` — the capable model agents run on by default (the `default-smart` model id)
- `visionModelId` — vision-capable model that describes images while indexing (the `default-vision` model id)
- `fastModelId` — fast utility model (the `default-fast` model id) used for lightweight tasks (session titles, compaction fallback, guardrail relevance evaluation fallback, knowledge summaries, HyDE query expansion)
- `modalityVsEmbeddingModelId` — embedding model per modality, keyed by the modality name, `TEXT` or `IMAGE` (e.g. `{"TEXT": "nomic-embed-text", "IMAGE": "arcface-image-embed"}`); used for Qdrant-backed memory and knowledge indexing and search

## 3.10 Knowledge Settings (`settings`)

Optional field on `Knowledge` bases. Controls chunking and indexing stages:

- `chunkingStrategy` (`List<ChunkingStrategy>`) — explicit ordered pipeline of chunking stages; each entry has a `type` plus stage-specific parameters
- `describeMedia` (`boolean`, default `true`) — whether media is described to text while indexing
- `modalityVsEmbeddingModelIds` (`Map<String, List<String>>`) — the embedding models that embed each modality (`TEXT`, `IMAGE`) in addition to the customer's default of that modality, which always embeds. Each model's vectors are chunks of their own that name the model (one per face for a face model); `search_knowledge` only matches those of the default models

*(Embedding models and chat models for LLM chunking/analysis are managed customer-wide via `DefaultModels`.)*

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
"settings": {
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
