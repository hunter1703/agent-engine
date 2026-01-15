package com.localagent.agents.shellagent.tools;

import com.localagent.engine.tools.AgentTool;
import com.localagent.engine.tools.ToolProvider;
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
    public AgentTool create(Map<String, Object> toolConfig, Map<String, Object> agentConfig) {
        Object value = toolConfig == null ? null : toolConfig.get("timeout_seconds");
        Duration timeout = Duration.ofMinutes(30);
        if (value instanceof Number number) {
            timeout = Duration.ofSeconds(number.longValue());
        } else if (value != null) {
            try {
                timeout = Duration.ofSeconds(Long.parseLong(value.toString()));
            } catch (NumberFormatException ignored) {
                timeout = Duration.ofMinutes(30);
            }
        }
        return new ShellCommandTool(timeout);
    }
}
