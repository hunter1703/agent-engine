# Codex CLI Integration Guide

This document explains how to integrate the Agent Engine with the Codex CLI using the Responses API endpoint.

## Overview

The Agent Engine provides a Responses API endpoint at `/agent/responses` that is compatible with the Codex CLI. This endpoint streams agent responses in the format expected by Codex, allowing seamless integration with the Codex CLI tool.

## Configuration

The Responses API can be configured using the following properties in `application.properties`:

```properties
# Enable the Responses API endpoint for Codex CLI compatibility (default: true)
agent.responses-api.enabled=true

# Default model name reported to Codex CLI (default: gpt-4-compatible)
agent.responses-api.default-model=gpt-4-compatible

# Include detailed token usage in responses (default: false)
agent.responses-api.include-token-usage=false

# Include reasoning/analysis steps in responses (default: true)
agent.responses-api.include-reasoning=true
```

## Using with Codex CLI

To use the Agent Engine with Codex CLI, you need to configure Codex to connect to your Agent Engine instance. Add the following to your Codex configuration file (`~/.codex/config.toml` or `.codex/config.toml`):

```toml
[model_providers.agent-engine]
name = "Agent Engine"
base_url = "http://localhost:8080"  # Replace with your Agent Engine URL
env_key = "AGENT_ENGINE_API_KEY"    # Optional: if you implement API key auth

[model_providers.agent-engine.http_headers]
# Add any required headers here
# Authorization = "Bearer ${AGENT_ENGINE_API_KEY}"

# Select the model provider for Codex
model = "agent-shell"
model_provider = "agent-engine"
```

## API Endpoint

The Responses API endpoint is located at:
- `POST /agent/responses`

Request body format:
```json
{
  "agentId": "shell_agent",
  "sessionId": "unique-session-id",
  "message": "Your message to the agent"
}
```

## Testing the Integration

To test the integration manually, you can use curl:

```bash
curl -N -X POST http://localhost:8080/agent/responses \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "agentId": "shell_agent",
    "sessionId": "test-session",
    "message": "What is the current directory?"
  }'
```

## Agent Configuration

Make sure your agent is properly configured in the `configs/agents/` directory. For example, `configs/agents/shell_agent.json`:

```json
{
  "engine": {
    "systemPrompt": "You are a command-line assistant with access to the run_cmd tool...",
    "model": {
      "id": "llama-cpp-python-local"
    },
    "tools": {
      "standardTools": [],
      "customTools": [
        "run_cmd"
      ]
    }
  },
  "context": {
    "summarizerModel": {
      "id": "llama-cpp-python-local"
    }
  }
}
```

## Troubleshooting

1. **Connection Issues**: Ensure the Agent Engine is running and accessible at the configured URL
2. **CORS Issues**: If accessing from a browser, ensure CORS is properly configured
3. **Authentication**: If you've implemented API key authentication, ensure the key is properly set in the Codex configuration
4. **Model Configuration**: Verify that the requested model is properly configured in the Agent Engine

## Development Notes

The Responses API implementation maps internal AGUI events to the Codex-compatible format:

- `RunStartedEvent` → `response.created`
- `StepStartedEvent` → `response.in_progress`
- `ThinkingStartEvent` → `response.output_item.added` (reasoning) + `response.reasoning_summary_part.added`
- `ThinkingEndEvent` → `response.output_item.done` (reasoning)
- `TextMessageStartEvent` → `response.output_item.added` (message)
- `TextMessageChunkEvent` → `response.output_text.delta`
- `TextMessageContentEvent`/`TextMessageEndEvent` → `response.output_item.done` (message)
- `ToolCallStartEvent` → `response.output_item.added` (with type `function_call`)
- `ToolCallEndEvent` → `response.output_item.done`
- `ToolCallResultEvent` → `response.output_item.added` (with type `function_call_output`)
- `RunFinishedEvent` → `response.completed`
- Mapper completion → `response.done`
- `RunErrorEvent` → `response.failed`

The implementation handles proper sequencing of output items and aggregates tool call arguments before emitting the
final `function_call` item.
