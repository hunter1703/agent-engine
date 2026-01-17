# REST Interface

The REST module exposes HTTP endpoints and SSE events for the agent runtime.

## Run
```bash
./gradlew :interfaces:rest:quarkusDev
```

## Protocol
The REST API accepts the shared `AgentRequest` JSON shape in request bodies. The `type` field
is accepted but not required because the endpoint defines the action.

### Request Format
Fields:
- `agentName`: required; selects the agent builder.
- `agentConfigPath`: optional path to agent config JSON/YAML.
- `sessionId`: optional session identifier; if omitted, one is generated.
- `message` / `user_message`: required for invoke.
- `type`: optional; ignored by REST endpoints.

### Endpoints
- `POST /agent/invoke`: run the agent for a single turn.
  - Response: `{ "sessionId": "...", "finalAnswer": "...", "thoughts": "..." }`.
- `POST /agent/prompt`: return the assembled prompt messages.
  - Response: `{ "sessionId": "...", "messages": [ { "role": "system", "content": "..." } ] }`.
- `GET /agent/events`: SSE stream of engine events.
  - Query params: `agentName`, `agentConfigPath`, `sessionId`.

### Examples
Invoke:
```json
{
  "agentName": "shell-agent",
  "agentConfigPath": "config/agents/shell.json",
  "message": "List files"
}
```

Build prompt:
```json
{
  "agentName": "shell-agent",
  "agentConfigPath": "config/agents/shell.json",
  "sessionId": "<existing-session-id>"
}
```

Events:
```
GET /agent/events?agentName=shell-agent&agentConfigPath=config/agents/shell.json&sessionId=<session-id>
```

### Event Payloads (SSE)
Each event is an `AgentEvent` object with `event`, `sessionId`, and a JSON `payload`.
- `event: "session"`: `{ "status": "ready" }` emitted on connect.
- `event: "tool_plan"`: list of `{ "id", "name", "args" }` tool calls.
- `event: "tool_execution"`: `{ "id", "tool_name", "status", "output", "duration_ms" }`.
- `event: "reasoning_start"` or `"reasoning_end"`: `{ "status": "start" | "end" }`.
- `event: "tool_repair"`: `{ "status": "repair" }`.
