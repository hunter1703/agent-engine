package com.agentengine.engine.tools;

import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.Annotations.Schema;
import java.util.Map;

public final class UserClarificationTool {
  public static final FunctionTool INSTANCE = FunctionTool.create(UserClarificationTool.class, "clarifyFromUser");

  private UserClarificationTool() {
  }

  @Schema(name = "user_clarification", description = "Request clarification from the user.")
  public static Map<String, Object> clarifyFromUser(
      @Schema(name = "question", description = "Clarifying question to present to the user") final String question) {
    return Map.of("clarification", question);
  }
}
