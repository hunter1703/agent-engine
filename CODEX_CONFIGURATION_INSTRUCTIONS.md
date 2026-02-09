# Codex CLI Configuration for Agent Engine

To connect Codex CLI to your Agent Engine instance, add the following configuration to your `~/.codex/config.toml` file:

```toml
# Set the default model to use the Agent Engine
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
base_url = "http://localhost:8080"  # Replace with your Agent Engine URL if different
# Optionally specify an API key if authentication is required
# env_key = "AGENT_ENGINE_API_KEY"

# Headers to send with each request to the Agent Engine
[model_providers.agent-engine.http_headers]
# Add any required authentication headers if needed
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

## Steps to Configure:

1. Open your Codex configuration file:
   ```bash
   nano ~/.codex/config.toml
   ```
   
2. Add the configuration above to the file

3. Save the file

4. Make sure your Agent Engine is running:
   ```bash
   cd /path/to/agent-engine
   ./gradlew :interfaces:rest:quarkusDev
   ```

5. Now you can use Codex CLI with your Agent Engine:
   ```bash
   codex
   # or
   codex --profile agent-engine-integration
   ```

## Notes:

- The Agent Engine must be running and accessible at the specified base_url
- The `/agent/responses` endpoint is automatically configured for Codex CLI compatibility
- To suppress tool call output in Codex, set `agent.responses-api.include-tool-events=false` in the Agent Engine config
- All configuration options can be adjusted based on your specific requirements
- If you're running the Agent Engine on a different port or host, update the base_url accordingly
