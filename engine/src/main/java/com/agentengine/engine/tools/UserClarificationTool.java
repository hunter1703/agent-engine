package com.agentengine.engine.tools;

import com.google.adk.tools.FunctionTool;
import java.util.Map;

public final class UserClarificationTool {
  public static final FunctionTool INSTANCE = FunctionTool.create(UserClarificationTool.class, "clarifyFromUser");

  private UserClarificationTool() {
  }

  public static Map<String, Object> clarifyFromUser(final String question) {
    return Map.of("clarification", question);
  }
}
