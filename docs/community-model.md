# Community Model

The community model enables agents to discover and invoke expert agents for specialized tasks.

## Architecture

### Expert Definition
Experts are regular agent configs stored in `configs/agents/community/experts/` with an optional `capabilities` field:

```json
{
  "type": "DEFAULT",
  "id": "title_generator",
  "name": "Title Generator",
  "description": "Generates concise, contextual titles",
  "capabilities": ["title_generation", "summarization"],
  "modelId": "qwen2.5-1.5b-instruct-q5_k_m",
  "systemPrompt": "...",
  "contextStrategy": { "type": "COMPACTION" },
  "tools": []
}
```

### Discovery
Agents use the `lookup_expert` tool to discover available experts:

```
lookup_expert() → {
  "experts": [
    {
      "expert_id": "title_generator",
      "name": "Title Generator",
      "description": "...",
      "capabilities": ["title_generation", "summarization"]
    }
  ],
  "count": 1
}
```

### Invocation
Once discovered, experts are invoked using existing agent tools:

```
1. lookup_expert() → returns expert_id
2. spawn_agent(agent_id=expert_id, message="...") → spawns expert as child
3. await_agent(child_session_id) → gets result
```

## Components

### CommunityRegistry
- **Interface**: `runtime/api/.../CommunityRegistry.java`
- **Implementation**: `runtime/.../CommunityRegistryImpl.java`
- Loads expert configs from `configs/agents/community/experts/` at startup
- Returns `List<BaseAgentConfig>` for all registered experts

### LookupExpertTool
- **Location**: `runtime/.../tools/community/LookupExpertTool.java`
- Discoverable tool (auto-registered via `@DiscoverableTool`)
- Returns all experts with their metadata
- Future: will support semantic search via `query` parameter

### BaseAgentConfig.capabilities
- **Location**: `util/agents/.../config/BaseAgentConfig.java`
- New field: `List<String> capabilities`
- Optional metadata for expert discovery
- Can be used for semantic matching (future)

## Usage Example

An agent that needs a title for a conversation:

```
Agent: lookup_expert()
→ Returns: [{"expert_id": "title_generator", "capabilities": ["title_generation"], ...}]

Agent: spawn_agent(agent_id="title_generator", message="Generate a title for: [conversation context]")
→ Returns: {"child_session_id": "session-xyz"}

Agent: await_agent(child_session_id="session-xyz")
→ Returns: {"result": {"title": "Agent Architecture Discussion"}}
```

## Current Limitations

1. **No semantic search**: `lookup_expert` returns all experts regardless of query
2. **No capability matching**: Agents must manually filter by capabilities
3. **No expert versioning**: Only one version of each expert can exist
4. **No runtime registration**: Experts must be present at startup

## Future Enhancements

1. **Semantic search**: Use embeddings to match queries to expert capabilities
2. **Capability-based filtering**: `lookup_expert(capability="title_generation")`
3. **Expert versioning**: Support multiple versions (e.g., `title_generator:v2`)
4. **Dynamic registration**: Allow experts to register at runtime
5. **MCP integration**: Expose external MCP servers as experts
6. **A2A federation**: Discover experts across multiple agent-engine clusters
7. **Expert analytics**: Track usage, success rates, latency per expert
