package com.agentengine.agents.shellagent.tools;

import com.agentengine.engine.beans.config.AgentConfig;
import com.agentengine.engine.tools.AgentTool;
import com.agentengine.engine.tools.ToolProvider;
import com.agentengine.engine.utils.CollectionUtils;
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
  public AgentTool create(final Map<String, Object> toolConfig, final AgentConfig agentConfig) {
    final Long timeoutSeconds = CollectionUtils.getLongValueFromMap(toolConfig, "timeout_seconds");
    final Duration timeout = Duration.ofSeconds(timeoutSeconds == null ? 30 : timeoutSeconds);
    return new ShellCommandTool(timeout);
  }
}

