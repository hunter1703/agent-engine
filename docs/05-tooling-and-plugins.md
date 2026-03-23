# 5. Tooling and Extension Points

## 5.1 Tool Runtime Contract

Tool APIs live in the active runtime modules:

- `Tool` (base class over ADK `BaseTool`)
- `ToolDescriptor`
- `ToolProvider`
- `ToolsetProvider`
- annotations: `@DiscoverableTool`, `@ToolConstructor`, `@ToolSchema`

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

Catalog behavior:

- built once for the runtime

`ToolFactory` handles runtime construction:

- selecting a standalone tool yields one `BaseTool`
- selecting a toolset yields one `BaseToolset`
- the runtime does not expand toolsets into individual tools before passing them to ADK
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

- static `DESCRIPTOR` field preferred
- fallback to no-arg constructor + `descriptor()`

## 5.4 Built-in Tools (Current Repository)

- `echo` (`EchoTool`)
- `run_cmd` (`ShellCommandTool`)
- `web_research` (`WebSearchTool` via connectors framework; supports quick DuckDuckGo lookup or detailed Brave Search)
- planning suite tools:
  - `create_plan`
  - `update_plan`
  - `add_task`
  - `update_task_info`
  - `start_task`
  - `complete_task`
  - `finish_plan`
  - `view_plan`
- internal `request_human_input` tool for ADK-native human input replay (auto-injected, not user-configurable)

## 5.5 Example: Shell Tool Safety Surface

`ShellCommandTool` characteristics:

- executes with `bash -lc`
- blocks `rm` pattern
- caps output to 12,000 chars
- configurable timeout (`timeout_seconds`)
- risk level marked `HIGH`

## 5.6 Extension Model

The current codebase exposes extension seams primarily through CDI-discovered providers and tool auto-discovery inside the runtime modules.

## 5.7 Prompt Protocol Resources

Runtime protocol text templates live under:

- `runtime/src/main/resources/prompts/shared/protocol/text.txt`

These are used when constructing final prompt instructions for model behavior/protocol conformance.
