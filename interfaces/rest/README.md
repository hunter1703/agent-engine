# REST Interface

The REST module exposes HTTP endpoints and SSE events for the agent runtime.

## Run
```bash
./gradlew :interfaces:rest:quarkusDev
```

## Protocol
The REST API accepts the shared `AgentRequest` JSON shape in request bodies. Use `type` to
switch between invoking the agent or building a prompt.

### Request Format
Fields:
- `agentName`: required; selects the agent builder (and config ID if Mongo is enabled).
- `agentConfigPath`: optional path to agent config JSON/YAML.
- `sessionId`: optional session identifier; if omitted, one is generated.
- `message`: required for invoke.
- `type`: optional; use `BUILD_PROMPT` to return the assembled prompt.

### Endpoints
- `POST /agent/invoke`: run the agent for a single turn (or build prompt when `type` is set).
  - Invoke response: `{ "sessionId": "...", "finalAnswer": "...", "thoughts": "..." }`.
  - Build prompt response: `{ "sessionId": "...", "messages": [ { "role": "system", "content": "..." } ] }`.
- `POST /agent/events`: SSE stream of engine events.
  - Request body: `agentName`, `agentConfigPath`, `sessionId`, `message`.

### Examples
Invoke:
```json
{
  "agentName": "shell_agent",
  "agentConfigPath": "configs/agents/shell_agent.json",
  "message": "List files"
}
```

Build prompt:
```json
{
  "agentName": "shell_agent",
  "agentConfigPath": "configs/agents/shell_agent.json",
  "sessionId": "<existing-session-id>",
  "type": "BUILD_PROMPT"
}
```

Events:
```json
{
  "agentName": "shell_agent",
  "sessionId": "<session-id>",
  "message": "List files"
}
```

When MongoDB is configured, omit `agentConfigPath` and the service loads configs from MongoDB
by `agentName`.

MongoDB is configured via `MONGODB_CONNECTION_STRING` and optional `CONFIG_DB_NAME` (default
`AGENT_ENGINE`).

### Event Payloads (SSE)
Each event is an `AgentEvent` object with `event`, `sessionId`, and a JSON `payload`.
- `event: "session"`: `{ "status": "ready" }` emitted on connect.
- `event: "tool_plan"`: list of `{ "id", "name", "args" }` tool calls.
- `event: "tool_execution"`: `{ "id", "tool_name", "status", "output", "duration_ms" }`.
- `event: "reasoning_start"` or `"reasoning_end"`: `{ "status": "start" | "end" }`.
- `event: "tool_repair"`: `{ "status": "repair" }`.
