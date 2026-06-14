# 5. Tooling and Extension Points

## 5.1 Tool Runtime Contract

Tool APIs live in the agent runtime modules (`agent:infra`, with shared annotations in `util:common`):

- `Tool` (base class over ADK `BaseTool`)
- `ToolDescriptor`
- `ToolProvider`
- `ToolsetProvider`
- annotations: `@DiscoverableTool`, `@ToolConstructor` (`agent:infra`), `@ToolSchema` (`util:common`)

All tools are expected to expose an `execute(...)` method.

Runtime composition uses ADK's native tool union model:

- `ToolProvider` creates one `BaseTool`
- `ToolsetProvider` creates one `BaseToolset`
- `LlmAgent` canonicalizes mixed `BaseTool`/`BaseToolset` lists at runtime

## 5.2 Tool Loading and Visibility

`ToolCatalog` composes the visible tool catalog from:

- CDI `ToolProvider` beans
- CDI `ToolsetProvider` beans
- auto-discovered annotated tools via `DiscoveredToolProviders`

Visibility rules:

- all registered tools are visible to all agents
- toolsets are shown as visible entries
- tools covered by toolsets are hidden from the top-level visible list to reduce duplication
- the catalog is built once for the runtime

`ToolFactory` handles runtime construction:

- selecting a standalone tool yields one `BaseTool`
- selecting a toolset yields one `BaseToolset`
- the runtime does not expand toolsets into individual tools before passing them to ADK

## 5.3 Auto-Discovered Tools (`DiscoveredToolProviders`)

`DiscoveredToolProviders` scans CDI `Tool` beans for `@DiscoverableTool`.

Constructor selection rules:

1. if one constructor has `@ToolConstructor`, use it
2. else if exactly one constructor exists, use it
3. else fail

Tool config binding:

- uses `@ToolSchema(name=...)` when present
- otherwise falls back to Java parameter names

Descriptor resolution:

- a static `DESCRIPTOR` field is preferred
- fallback to a no-arg constructor + `descriptor()`

## 5.4 Built-in Tools

**Utility**
- `echo` (`EchoTool`) — echoes input back; useful for testing

**Shell**
- `run_cmd` (`ShellCommandTool`) — execute shell commands; see §5.5 for the safety surface

**Web**
- `web_research` (`WebSearchTool` via the connectors framework; quick DuckDuckGo lookup or detailed Brave Search)

**File operations** (suite name: `file_tools`)
- `read_file` — read file contents with pagination (`offset` / `limit`)
- `list_dir` — list directory contents with configurable depth
- `grep_files` — search files with a regex pattern
- `apply_patch` — apply a unified diff patch to files with validation

**Planning** (suite name: `planning`)
- `create_plan`, `update_plan`, `add_task`, `update_task_info`, `start_task`, `complete_task`, `finish_plan`, `view_plan`

**Agent coordination** (suite name: `agent_tools`)
- `spawn_agent` — start a child agent session for a subtask; returns `child_session_id`
- `send_message` — send a follow-up message to an existing child session
- `await_agent` — wait for a child session to finish and collect its result
- `lookup_expert` — discover available community experts with their IDs and capabilities

**Knowledge**
- `search_knowledge` — semantic search over indexed knowledge chunks; accepts `query`, optional `knowledgeIds` to scope to specific documents, `offset`, and `limit`; returns `{ chunks, total, offset, limit }`

**Internal** (auto-injected, not user-configurable)
- `request_human_input` — ADK-native human input replay for pause/resume flows

## 5.5 Example: Shell Tool Safety Surface

`ShellCommandTool` characteristics:

- executes with `bash -lc`
- blocks the `rm` pattern
- caps output to 12,000 chars
- configurable timeout (`timeout_seconds`)
- risk level marked `HIGH`

## 5.6 Extension Model

The codebase exposes extension seams primarily through CDI-discovered providers and tool auto-discovery inside the agent runtime modules (`agent:infra` / `agent:core`).

## 5.7 Prompt Protocol Resources

Runtime protocol text templates live under:

- `agent/core/src/main/resources/prompts/prompts/shared/protocol/text.txt`

These are used when constructing final prompt instructions for model behavior/protocol conformance, and are rendered via Jinjava.
