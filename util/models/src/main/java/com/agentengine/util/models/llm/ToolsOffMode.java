package com.agentengine.util.models.llm;

/** How a request tells the model it may not call tools on this turn. */
public enum ToolsOffMode {
  /** The tool definitions stay in the request and {@code tool_choice} is set to {@code none}. */
  SEND_TOOL_CHOICE_NONE,

  /** The tool definitions are left out of the request. */
  REMOVE_TOOL_DEFINITIONS
}
