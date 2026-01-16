package com.localagent.agents.shellagent.tools;

import com.localagent.engine.beans.config.AgentConfig;
import com.localagent.engine.tools.AgentTool;
import com.localagent.engine.tools.ToolProvider;
import com.localagent.engine.utils.CollectionUtils;

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
    public AgentTool create(Map<String, Object> toolConfig, AgentConfig agentConfig) {
        final Long timeoutSeconds = CollectionUtils.getLongValueFromMap(toolConfig, "timeout_seconds");
        final Duration timeout = Duration.ofSeconds(timeoutSeconds == null ? 30 : timeoutSeconds);
        return new ShellCommandTool(timeout);
    }
}
