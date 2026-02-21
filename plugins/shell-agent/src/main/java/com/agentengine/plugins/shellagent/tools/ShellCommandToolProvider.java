package com.agentengine.plugins.shellagent.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.ToolProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import java.time.Duration;
import java.util.Map;

public final class ShellCommandToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "ALL";
  }

  @Override
  public String toolName() {
    return "run_cmd";
  }

  @Override
  public BaseTool create(final AgentContext agentContext, final Map<String, Object> toolConfig) {
    final Long timeoutSeconds = parseTimeoutSeconds(toolConfig);
    final Duration timeout = Duration.ofSeconds(timeoutSeconds == null ? 30 : timeoutSeconds);
    return FunctionTool.create(new ShellCommandTool(timeout), "runCommand");
  }

  private static Long parseTimeoutSeconds(final Map<String, Object> toolConfig) {
    if (toolConfig == null || toolConfig.isEmpty()) {
      return null;
    }
    final Object value = toolConfig.get("timeout_seconds");
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Long.parseLong(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
