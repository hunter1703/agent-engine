package com.agentengine.engine.tools;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;

  /**
   * A phantom tool that acts as a signal that the agent is ready with the final answer.
   * Pre-submission text is treated as thoughts; post-submission text is the final answer.
   */
public final class SubmitFinalAnswerTool extends Tool {
  public static final String TOOL_NAME = "submit_final_answer";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "MANDATORY: You MUST call this tool to signal you are ready to provide the final answer to the user. "
              + "This tool call MUST be a clean turn: DO NOT include user-facing prose, internal thoughts, or other tool calls in the same turn. "
              + "After calling this tool, you will be prompted to provide the actual answer text in the following turn. "
              + "Any text provided BEFORE or ALONGSIDE this tool call will NOT be seen by the user.",
          List.of(ALL),
          Map.of());
  public static final SubmitFinalAnswerTool INSTANCE = new SubmitFinalAnswerTool();

  public SubmitFinalAnswerTool() {
    super(DESCRIPTOR);
  }

  @Override
  public ToolDescriptor descriptor() {
    return DESCRIPTOR;
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext) {
    return Map.of("status", "success", "message", "Propose your final answer now.");
  }
}
