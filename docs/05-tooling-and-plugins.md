# 5. Tooling and Plugins

## 5.1 Tool Runtime Contract

Tool API is defined in `engine:api`:

- `Tool` (base class over ADK `BaseTool`)
- `ToolDescriptor`
- `ToolProvider`
- `ToolSuite`
- annotations: `@AgentTool`, `@ToolConstructor`, `@ToolSchema`

All tools are expected to expose an `execute(...)` method.

## 5.2 Tool Loading and Visibility

`ToolRegistry` composes tool catalogs from:

- CDI `ToolProvider` beans
- plugin `ToolProvider` implementations loaded via `ServiceLoader`
- `ToolSuite` registrations from CDI, providers, and plugin classloader

Visibility rules:

- if descriptor `agentIds` is empty -> visible to all
- if includes `ALL` -> visible to all
- else tool is visible only to listed agent IDs

Catalog behavior:

- cached per agent ID
- suites are shown as visible entries
- tools covered by suites are hidden from top-level visible list to reduce duplication

## 5.3 Auto-Discovered Tools (`AgentToolProvider`)

`AgentToolProvider` scans CDI `Tool` beans for `@AgentTool`.

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
- `web_lookup` (`WebLookupTool` via connectors framework)
- planning suite tools:
  - `create_plan`
  - `update_plan`
  - `add_task`
  - `update_task_info`
  - `start_task`
  - `complete_task`
  - `finish_plan`
  - `view_plan`
- `user_clarification` tool for pause/clarification flow

## 5.5 Example: Shell Tool Safety Surface

`ShellCommandTool` characteristics:

- executes with `bash -lc`
- blocks `rm` pattern
- caps output to 12,000 chars
- configurable timeout (`timeout_seconds`)
- risk level marked `HIGH`

## 5.6 Plugin Loading Model

`PluginLoader` resolves plugin directory in this order:

1. `PLUGIN_DIR` system property
2. `PLUGIN_DIR` environment variable
3. upward search for `plugins/` from current working directory
4. fallback literal `plugins`

All `*.jar` files in plugin directory are added to a dedicated `URLClassLoader`.

Plugin-extensible areas include:

- `ToolProvider`
- `ToolSuite`
- `GuardrailProvider`

via Java `ServiceLoader` on plugin classloader.

## 5.7 Prompt Protocol Resources

Engine protocol text templates live under:

- `engine/src/main/resources/prompts/shared/protocol/json.txt`
- `engine/src/main/resources/prompts/shared/protocol/text.txt`

These are used when constructing final prompt instructions for model behavior/protocol conformance.
