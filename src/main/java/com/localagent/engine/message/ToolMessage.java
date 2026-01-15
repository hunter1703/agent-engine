package com.localagent.engine.message;

import java.util.Map;

public class ToolMessage extends Message {

    private final String toolName;

    public ToolMessage(final String content, final String toolName, final Map<String, Object> toolArgs) {
        super(Role.TOOL, content);
        this.toolName = toolName;
        this.toolArgs = toolArgs;
    }

    private final Map<String, Object> toolArgs;

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getToolArgs() {
        return toolArgs;
    }

    public static Message tool(String content, String toolName, Map<String, Object> toolArgs) {
        return new ToolMessage(content, toolName, toolArgs);
    }
}
