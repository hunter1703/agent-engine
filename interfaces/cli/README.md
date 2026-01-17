# CLI Interface

The CLI module runs the stdio JSON protocol against the engine.

## Run
```bash
./gradlew :interfaces:cli:run --args="server"
```

The CLI reads JSON lines on stdin and writes JSON events on stdout. It emits engine
events verbatim (for example: `tool_plan`, `tool_execution`, `reasoning_start`).

## Protocol
The CLI accepts the shared `AgentRequest` JSON shape over stdin.

### Request Format
Fields:
- `id`: required request identifier, echoed in responses.
- `type`: required; supports `INVOKE_AGENT` and `BUILD_PROMPT`.
- `message` / `user_message`: required for `INVOKE_AGENT`.

Request types:
- `INVOKE_AGENT`: run the agent with the provided `message`.
- `BUILD_PROMPT`: return the assembled prompt messages for the current session.
- `BUILD_EVENT`: reserved for future use.
- `STOP_AGENT`: reserved for future use.

Example requests:
```json
{"id":"req-1","type":"INVOKE_AGENT","message":"hello"}
```
```json
{"id":"req-2","type":"BUILD_PROMPT"}
```

### Response Format
The CLI prints one JSON object per line:
- `INVOKE_AGENT` emits events:
  - `{"sessionId":"<request id>","event":"thoughts","text":"..."}` (optional)
  - `{"sessionId":"<request id>","event":"finalAnswer","text":"..."}`
- `BUILD_PROMPT` emits a result object:
  - `{"id":"<request id>","result":{"messages":[{"role":"system","content":"..."}]}}`

### Engine Events
Tool and lifecycle events from the engine are passed through as JSON lines with:
- `sessionId`: engine session id.
- `event`: one of `tool_plan`, `tool_execution`, `reasoning_start`, `reasoning_end`, `tool_repair`.
- event-specific payload fields described in `interfaces/rest/README.md`.
