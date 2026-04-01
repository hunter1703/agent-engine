package com.agentengine.runtime.tools.echo;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.annotations.ToolConstructor;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.StringUtils;
import java.util.Map;

@DiscoverableTool
public final class EchoTool extends Tool {
    private static final String TOOL_NAME = "echo";
    public static final ToolDescriptor DESCRIPTOR =
            new ToolDescriptor(TOOL_NAME, "Echoes input text with an optional prefix.");
    private final String prefix;

    public EchoTool() {
        this(null);
    }

    @ToolConstructor
    public EchoTool(
            @ToolSchema(name = "prefix", description = "Prefix to add to the echoed message", optional = true)
                    final String prefix) {
        super(DESCRIPTOR);
        this.prefix = prefix;
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "text", description = "The text to echo") final String text,
            @ToolSchema(name = "prefix", description = "Optional prefix to prepend", optional = true)
                    final String prefix) {
        final String resolvedPrefix = StringUtils.isNotBlank(prefix) ? prefix : this.prefix;
        final String resolvedText = text == null ? "" : text;
        final String combined = (resolvedPrefix == null ? "" : resolvedPrefix) + resolvedText;
        return Map.of("output", combined);
    }
}
