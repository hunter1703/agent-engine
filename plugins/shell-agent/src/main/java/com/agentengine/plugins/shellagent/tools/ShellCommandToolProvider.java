package com.agentengine.plugins.shellagent.tools;

import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolProvider;
import java.time.Duration;
import java.util.Map;

public final class ShellCommandToolProvider implements ToolProvider {
  @Override
  public String agentId() {
    return "shell_agent";
  }

  @Override
  public String toolName() {
    return "run_cmd";
  }

  @Override
  public Tool create(final Map<String, Object> toolConfig) {
    final Long timeoutSeconds = parseTimeoutSeconds(toolConfig);
    final Duration timeout = Duration.ofSeconds(timeoutSeconds == null ? 30 : timeoutSeconds);
    return new ShellCommandTool(timeout);
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
