package com.agentengine.engine.tools.shell;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Singleton
public final class ShellCommandToolProvider implements ToolProvider {

  @Override
  public List<ToolDescriptor> tools() {
    return List.of(ShellCommandTool.DESCRIPTOR);
  }

  @Override
  public Tool create(final AgentContext agentContext, final String toolName, final Map<String, Object> toolConfig) {
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
