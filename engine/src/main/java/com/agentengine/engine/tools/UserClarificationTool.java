package com.agentengine.engine.tools;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.AgentTool;
import com.agentengine.engine.api.tools.annotations.ToolSchema;

import java.util.List;
import java.util.Map;

@AgentTool
public final class UserClarificationTool extends Tool {
  private static final String TOOL_NAME = "user_clarification";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, "Request clarification from the user.", List.of(ALL), Map.of());

  public UserClarificationTool() {
    super(DESCRIPTOR);
  }

  public static Map<String, Object> clarifyFromUser(final String question) {
      return Map.of("clarification", question);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "question", description = "Clarifying question to present to the user")
          final String question) {
    return clarifyFromUser(question);
  }
}
