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

## Protocol
The REST API accepts the shared `AgentRequest` JSON shape in request bodies. Use `type` to
switch between invoking the agent or building a prompt for `POST /v1/invoke`.

### Request Format
Fields:
- `agentId`: required; selects the agent builder (and config ID if Mongo is enabled).
- `agentConfigPath`: optional path to agent config JSON/YAML.
- `sessionId`: optional session identifier; if omitted, one is generated.
- `message`: required for invoke and events.
- `type`: required for `/v1/invoke`; use `INVOKE_AGENT` or `BUILD_PROMPT`.

### Endpoints
- `POST /v1/invoke`: run the agent for a single turn (`INVOKE_AGENT`) or build prompt (`BUILD_PROMPT`).
  - Invoke response: `{ "sessionId": "...", "finalAnswer": "...", "thoughts": "..." }`.
  - Build prompt response: `{ "sessionId": "...", "messages": [ { "role": "system", "content": "..." } ] }`.
- `POST /v1/events`: SSE stream of engine events.
  - Request body: `agentId`, `agentConfigPath`, `sessionId`, `message`.
- `POST /v1/responses`: SSE stream of responses API events.
- `GET /v1/agents`: list agent configurations.
- `GET /v1/agents/{agentId}`: retrieve agent configuration.
- `POST /v1/agents`: create an agent configuration.
- `PUT /v1/agents/{agentId}`: update an agent configuration.
- `DELETE /v1/agents/{agentId}`: delete an agent configuration.
- `GET /v1/agents/{agentId}/sessions`: list sessions for an agent (supports `pageSize`, `pageToken`, `userId`).
- `GET /v1/agents/{agentId}/sessions/{sessionId}`: get session details and paginated events.
- `POST /v1/catalog/search`: resource catalog search API (supports agents, sessions, and other resource types).
- `GET /v1/catalog/{resourceType}/{id}`: resource catalog retrieval API.

For catalog queries targeting the `session` asset type, pass `options.includeEvents=true` in the
request body to include AG-UI event payloads in each session result.

### Examples
Invoke:
```json
{
  "type": "INVOKE_AGENT",
  "agentId": "shell_agent",
  "agentConfigPath": "configs/agents/shell_agent.json",
  "message": "List files"
}
```

Build prompt:
```json
{
  "type": "BUILD_PROMPT",
  "agentId": "shell_agent",
  "agentConfigPath": "configs/agents/shell_agent.json",
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

When MongoDB is configured, omit `agentConfigPath` and the service loads configs from MongoDB
by `agentName`.

MongoDB is configured via the environment variable `MONGODB_CONNECTION_STRING` (default
`mongodb://localhost:27002`). The database name is currently fixed to `AGENT_ENGINE`.

### Event Payloads (SSE)
Each event is an AG-UI `BaseEvent` JSON payload, compatible with the AG-UI protocol.
Common events include:
- `RUN_STARTED` / `RUN_FINISHED` / `RUN_ERROR`
- `STEP_STARTED` / `STEP_FINISHED`
- `TEXT_MESSAGE_START` / `TEXT_MESSAGE_CONTENT` / `TEXT_MESSAGE_END`
- `TOOL_CALL_START` / `TOOL_CALL_ARGS` / `TOOL_CALL_END` / `TOOL_CALL_RESULT`

The server also emits `ThinkingTextMessage*` events with raw metadata for reasoning deltas and emits
planning data via standard `TOOL_CALL_*` events.
