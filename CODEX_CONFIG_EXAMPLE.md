# Codex CLI Configuration for Agent Engine Integration

This document provides an example configuration for connecting the Codex CLI to the Agent Engine's Response API endpoint.

## Prerequisites

Before configuring Codex CLI, ensure that:
1. The Agent Engine REST service is running (typically on `http://localhost:8080`)
2. You have the appropriate agent configurations in the `configs/agents/` directory
3. The required models are configured in the `configs/models/` directory

## Codex Configuration

Create or update your Codex configuration file at `~/.codex/config.toml` with the following content:

```toml
# Default model to use
model = "agent-engine-model"

# Approval policy - controls when Codex pauses to ask for approval before running commands
approval_policy = "on-request"

# Sandbox mode - controls filesystem and network access
sandbox_mode = "workspace-write"

# Web search mode
web_search = "cached"

# Define the Agent Engine as a model provider
[model_providers.agent-engine]
name = "Agent Engine"
base_url = "http://localhost:8080"
# Optionally specify an API key if authentication is required
# env_key = "AGENT_ENGINE_API_KEY"

# Headers to send with each request to the Agent Engine
[model_providers.agent-engine.http_headers]
# Add any required authentication headers
# Authorization = "Bearer ${AGENT_ENGINE_API_KEY}"

# Configure the model to use the Agent Engine provider
[models."agent-engine-model"]
name = "Agent Engine Model"
provider = "agent-engine"
# Set context window size if known
# context_window = 128000

# Optional: Configure specific behavior for the Agent Engine
[models."agent-engine-model".responses]
# Enable response streaming
stream = true
# Set response timeout if needed
timeout_seconds = 300

# Optional: Set up a profile for the Agent Engine integration
[profiles.agent-engine-integration]
model = "agent-engine-model"
approval_policy = "on-request"
sandbox_mode = "workspace-write"
model_reasoning_effort = "medium"

# To use this profile, run: codex --profile agent-engine-integration
```

## Agent Configuration

Make sure you have a properly configured agent in your `configs/agents/` directory. For example, `configs/agents/shell_agent.json`:

```json
{
  "engine": {
    "systemPrompt": "You are a command-line assistant with access to the run_cmd tool. Use it to inspect the environment or execute commands when needed, preferring safe, read-only operations. Avoid destructive actions unless explicitly requested, ask for confirmation when commands could modify data, and summarize results clearly without fabricating output.",
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

## Using Codex with Agent Engine

Once configured, you can use Codex CLI with the Agent Engine:

```bash
# Use the default configuration
codex

# Or use the specific profile
codex --profile agent-engine-integration

# Or specify the model directly
codex --model agent-engine-model
```

## Testing the Connection

To test the connection to the Agent Engine's Response API endpoint, you can make a direct request:

```bash
curl -N -X POST http://localhost:8080/agent/responses \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "agentId": "shell_agent",
    "sessionId": "test-session-'$(date +%s)'",
    "message": "What is the current directory?"
  }'
```

## Troubleshooting

1. **Connection refused**: Ensure the Agent Engine REST service is running on the configured port
2. **Authentication errors**: Check that any required API keys or headers are properly configured
3. **Model not found**: Verify that the agent configuration exists and is accessible
4. **CORS issues**: If connecting from a browser-based interface, ensure CORS is properly configured

## Advanced Configuration

For more advanced configurations, you can also set up environment variables:

```bash
export AGENT_ENGINE_API_KEY="your-api-key-here"
```

Then reference it in the configuration using `${AGENT_ENGINE_API_KEY}` syntax.

## Notes

- The Agent Engine's `/agent/responses` endpoint is specifically designed for Codex CLI compatibility
- The endpoint streams responses in the Response API format that Codex expects
- All standard Codex features like approval policies and sandboxing work with the Agent Engine integration