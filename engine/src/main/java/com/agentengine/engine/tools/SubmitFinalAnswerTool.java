package com.agentengine.engine.tools;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.AgentTool;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.utils.FinalAnswerUtils;
import com.google.adk.tools.ToolContext;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

  /**
   * A phantom tool that acts as a signal that the agent is ready with the final answer.
   * Pre-submission text is treated as thoughts; post-submission text is the final answer.
   */
@AgentTool
@Singleton
public final class SubmitFinalAnswerTool extends Tool {
  public static final String TOOL_NAME = FinalAnswerUtils.TOOL_NAME;
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Explicitly signals that you are ready to provide the final answer to the user. "
              + "All text emitted AFTER this tool call will be treated as the final answer. "
              + "All text emitted BEFORE this tool call is treated as internal thoughts/reasoning. "
              + "Call this tool only when you are ready to provide the final answer.",
          List.of(ALL),
          Map.of());

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
