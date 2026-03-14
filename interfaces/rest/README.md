# REST Interface

The REST module exposes HTTP endpoints and SSE events for the agent runtime.

## Run
```bash
./gradlew :interfaces:rest:quarkusDev
```

## OpenAPI
Generate the spec files with:
```bash
./gradlew :interfaces:rest:quarkusBuild
```

The schemas are written to `interfaces/rest/build/openapi/agent-engine.json` and
`interfaces/rest/build/openapi/agent-engine.yaml`. When running in dev mode, the spec is
also available at `http://localhost:8080/q/openapi` (append `?format=yaml` for YAML).

## Builder Contracts
- `GET /schemas/{assetType}` serves generated Builder Contract V2 for `agent` and `model`.
- Contracts are generated from config classes/annotations, cached in memory, and served with ETag.
- Optional `mode` query parameter (`create`, `edit`, `view`) resolves field visibility/editability.

## Protocol
The REST API accepts the shared `AgentRequest` JSON shape in request bodies. Use `type` to
switch between invoking the agent or building a prompt for `POST /v1/invoke`.

### Request Format
Fields:
- `agentId`: required; selects the agent builder (and config ID if Mongo is enabled).
- `sessionId`: optional session identifier; if omitted, one is generated.
- `message`: required for invoke and events.
- `type`: required for `/v1/invoke`; use `INVOKE_AGENT` or `BUILD_PROMPT`.

### Endpoints
- `POST /v1/agent/events`: SSE stream of AG-UI runtime events.
  - Request body: `agentId`, `sessionId`, `message`.
- `POST /v1/agent/session/resume/events`: SSE stream when resuming a paused run.
- `POST /v1/agent/agent`: create an agent configuration.
- `PUT /v1/agent/agent/{agentId}`: update an agent configuration.
- `DELETE /v1/agent/agent/{agentId}`: delete an agent configuration.
- `POST /v1/model`: create a model configuration.
- `PUT /v1/model/{modelId}`: update a model configuration.
- `DELETE /v1/model/{modelId}`: delete a model configuration.
- `GET /v1/agents`: list agent configurations.
- `GET /v1/agents/{agentId}`: retrieve agent configuration.
- `POST /v1/agents`: create an agent configuration.
- `PUT /v1/agents/{agentId}`: update an agent configuration.
- `DELETE /v1/agents/{agentId}`: delete an agent configuration.
- `GET /v1/agents/{agentId}/sessions`: list sessions for an agent (supports `pageSize`, `pageToken`, `userId`).
- `GET /v1/agents/{agentId}/sessions/{sessionId}`: get session details and paginated events.
- `POST /v1/catalog/search`: resource catalog search API (supports agents, sessions, and other resource types).
- `GET /v1/catalog/{resourceType}/{id}`: resource catalog retrieval API.
- `GET /v1/models`: OpenAI-compatible model list (`model id == agent id`).
- `POST /v1/chat/completions`: OpenAI-compatible chat completions (streaming and non-streaming).
- `POST /v1/responses`: OpenAI-compatible Responses API events (streaming and non-streaming).
- `POST /v1/embeddings`: returns `501 not implemented` (explicitly unsupported in v1).

For catalog queries targeting the `session` asset type, pass `options.includeEvents=true` in the
request body to include AG-UI event payloads in each session result.

### Examples
Invoke:
```json
{
  "type": "INVOKE_AGENT",
  "agentId": "shell_agent",
  "message": "List files"
}
```

Build prompt:
```json
{
  "type": "BUILD_PROMPT",
  "agentId": "shell_agent",
  "sessionId": "<existing-session-id>"
}
```

Events:
```json
{
  "agentId": "shell_agent",
  "sessionId": "<session-id>",
  "message": "List files"
}
```

Agent configuration is resolved from MongoDB by `agentId`.

MongoDB is configured via the environment variable `MONGODB_CONNECTION_STRING` (default
`mongodb://localhost:27017`). The database name is currently fixed to `AGENT_ENGINE`.

### Event Payloads (SSE)
Each event is an AG-UI `BaseEvent` JSON payload, compatible with the AG-UI protocol.
Common events include:
- `RUN_STARTED` / `RUN_FINISHED` / `RUN_ERROR`
- `STEP_STARTED` / `STEP_FINISHED`
- `TEXT_MESSAGE_START` / `TEXT_MESSAGE_CONTENT` / `TEXT_MESSAGE_END`
- `TOOL_CALL_START` / `TOOL_CALL_ARGS` / `TOOL_CALL_END` / `TOOL_CALL_RESULT`
- `CUSTOM` events with `name=CORRECTION` in `rawEvent` for corrective prompts

The server also emits `ThinkingTextMessage*` events with raw metadata for reasoning deltas and emits
planning data via standard `TOOL_CALL_*` events.

## Open WebUI Integration
- Configure Open WebUI `OPENAI_API_BASE_URL` (or `OPENAI_API_BASE_URLS`) to this service `/v1` base URL.
- Enable `ENABLE_FORWARD_USER_INFO_HEADERS=true` in Open WebUI so chat-id forwarding is sent.
- By default, agent-engine reads `X-OpenWebUI-Chat-Id` and uses it for deterministic session continuity.
- The forwarded chat-id header name can be overridden with `agentengine.responses.forwarded-chat-id-header` (legacy `agentengine.openai.forwarded-chat-id-header` is also honored).
- Responses streams include reasoning, correction, and tool lifecycle visibility through `response.*` events.
