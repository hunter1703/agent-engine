package com.agentengine.plugins.shellagent.tools;

import com.agentengine.engine.tools.Tool;
import com.agentengine.engine.tools.ToolProvider;
import com.agentengine.commons.utils.CollectionUtils;
import java.time.Duration;
import java.util.Map;

public final class ShellCommandToolProvider implements ToolProvider {
  @Override
  public String agentName() {
    return "shell_agent";
  }

  @Override
  public String toolName() {
    return "run_cmd";
  }

  @Override
  public Tool create(final Map<String, Object> toolConfig) {
    final Long timeoutSeconds = CollectionUtils.getLongValueFromMap(toolConfig, "timeout_seconds");
    final Duration timeout = Duration.ofSeconds(timeoutSeconds == null ? 30 : timeoutSeconds);
    return new ShellCommandTool(timeout);
  }
}
