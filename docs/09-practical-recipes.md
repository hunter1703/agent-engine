# 9. Practical Recipes

## 9.1 Create and Use a Model

```bash
curl -s -X POST http://localhost:18080/v1/model/upsert \
  -H 'Content-Type: application/json' \
  -d '{
    "id":"local-qwen",
    "name":"Local Qwen",
    "type":"open_ai_compatible",
    "model":"qwen2.5-1.5b-instruct-q5_k_m",
    "baseUrl":"http://127.0.0.1:17000/v1"
  }'
```

## 9.2 Create a Default Agent

```bash
curl -s -X POST http://localhost:18080/v1/agent/agent/upsert \
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

## 9.3 Stream Agent Events

```bash
curl -N -X POST http://localhost:18080/v1/agent/events \
  -H 'Content-Type: application/json' \
  -d '{
    "type":"STREAM_AGUI_EVENTS",
    "agentId":"echo-agent",
    "message":"hello"
  }'
```

## 9.4 Resume a Paused Session

```bash
curl -N -X POST http://localhost:18080/v1/agent/session/<session-id>/resume/events \
  -H 'Content-Type: application/json' \
  -d '{"message":"approved"}'
```

## 9.5 List Models via Catalog

```bash
curl -s -X POST http://localhost:18080/v1/catalog/list \
  -H 'Content-Type: application/json' \
  -d '{"assetType":"model"}'
```

## 9.6 Use Schema Endpoint

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
