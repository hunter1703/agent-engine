# TODO

- Emit `tasks.md`, `walkthrough.md`, and `implementation.md` artifacts from the plan/tasks/walkthrough agents.
- Only add user message to tool assistant context if in that turn tool assistant is getting invoked; do not add if in a given turn response is given only by reasoner agent
- Make ToolRegistry a service
- ToolOutputFormatted and ToolPromptUtils can be merged into ToolUtils
- Need of ToolOutput and ToolExecution both
- Need of so many tool related abstractions : ToolExecutor, ToolRegistry, ToolProvider, Tool, ToolCall, ToolCallRuntime, ToolRouter, ToolContext, ToolExecution, ToolOutput, ToolHandler, ToolResult, ToolOutputFormatter, ToolPromptUtils
- Need of separate assistant agent (compare with codex)
- remove trivial tests
- add integration tests
- Rewrite and restructure prompts
- 
