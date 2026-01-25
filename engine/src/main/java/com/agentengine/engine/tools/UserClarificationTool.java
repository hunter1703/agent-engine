package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.utils.JsonUtils;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

@Singleton
public final class UserClarificationTool implements Tool {
  public static final String NAME = "user_clarification";
  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Request additional input from the user when required.";
  }

  @Override
  public String execute(final Map<String, Object> args) {
    Map<String, Object> payload = new HashMap<>();
    if (args != null) {
      payload.putAll(args);
    }
    return JsonUtils.toJson(payload);
  }

  @Override
  public ToolResult executeWithContext(final ToolContext context, final Map<String, Object> args) {
    return ToolResult.clarification(execute(args));
  }
}
