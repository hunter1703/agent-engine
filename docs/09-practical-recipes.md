# 9. Practical Recipes

These examples assume the REST gateway is reachable at `http://localhost:18080` (adjust to your port-forward or service address; the gateway listens on `8080` in-cluster).

## 9.1 Create a Chat Model

```bash
curl -s -X POST http://localhost:18080/v1/model/upsert \
  -H 'Content-Type: application/json' \
  -d '{
    "id":"local-qwen",
    "name":"Local Qwen",
    "type":"CHAT",
    "provider":"OPEN_AI_COMPATIBLE",
    "model":"qwen2.5-1.5b-instruct-q5_k_m",
    "baseUrl":"http://127.0.0.1:17000/v1",
    "apiKey":""
  }'
```

## 9.2 Create a Default Agent

```bash
curl -s -X POST http://localhost:18080/v1/agent/upsert \
  -H 'Content-Type: application/json' \
  -d '{
    "id":"echo-agent",
    "type":"DEFAULT",
    "name":"Echo Agent",
    "modelId":"local-qwen",
    "systemPrompt":"You are concise.",
    "contextStrategy":{"type":"COMPACTION"},
    "tools":[{"toolName":"echo","configs":{"prefix":"ok-"}}]
  }'
```

## 9.3 Invoke an Agent (stream AG-UI events)

```bash
curl -N -X POST http://localhost:18080/v1/agent/echo-agent/invoke \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "runId":"run-1",
    "messages":[{"id":"m1","role":"user","content":"hello"}]
  }'
```

Omit `threadId` to start a new session; pass it to continue an existing one.

## 9.4 Re-attach and Resume a Session

```bash
# Re-attach to a session's event stream (replays committed history, then live events)
curl -N "http://localhost:18080/v1/session/<session-id>/stream?liveOnly=false"

# Provide a confirmation / human input for a paused session
curl -s -X POST "http://localhost:18080/v1/session/<session-id>/confirm/<confirmation-id>" \
  -H 'Content-Type: application/json' \
  -d '{"answer":"approved"}'
```

See [`10-protocol-and-guarantees.md`](./10-protocol-and-guarantees.md) §10.3.1 for the confirmation payload semantics (binary `ALLOW`/`DISALLOW` vs. text `answer`).

## 9.5 List Models via Catalog

```bash
curl -s -X POST http://localhost:18080/v1/catalog/list \
  -H 'Content-Type: application/json' \
  -d '{"assetType":"model"}'
```

## 9.6 Use the Schema Endpoint

```bash
curl -s http://localhost:18080/schemas/model | jq .
```

## 9.7 Minimal Parallel Orchestrator Config

```json
{
  "id": "parallel-manager",
  "type": "ORCHESTRATOR",
  "name": "Parallel Manager",
  "systemPrompt": "Coordinate the parallel worker agents and aggregate their outputs.",
  "orchestrationMode": "PARALLEL",
  "subAgentIds": ["agent-a", "agent-b", "agent-c"],
  "parallel": {
    "aggregationPolicy": "BEST_EFFORT",
    "stoppingPolicy": "QUORUM",
    "quorum": 2
  },
  "contextStrategy": { "type": "COMPACTION" }
}
```

## 9.8 Minimal Guardrails Block Example

```json
{
  "enabled": true,
  "defaultOnError": "FAIL_CLOSED",
  "rules": [
    {
      "id": "block-risky-tools",
      "type": "TOOL_SAFETY",
      "stage": "TOOL",
      "enabled": true,
      "action": "BLOCK",
      "message": "Tool use blocked by policy"
    }
  ]
}
```
