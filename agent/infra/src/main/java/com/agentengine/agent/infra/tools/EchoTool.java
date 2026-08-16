package com.agentengine.agent.infra.tools;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.annotations.ToolConstructor;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import java.util.Map;

@DiscoverableTool
public final class EchoTool extends Tool {
  private static final String TOOL_NAME = "echo";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Returns the provided text prepended with an optional prefix string. Use to verify that tool "
              + "invocation is functioning correctly, or to produce a known fixed value in a diagnostic "
              + "workflow — not for transforming, computing, or processing content. The output is always the "
              + "literal concatenation of prefix and text. "
              + "Returns: { output: \"<prefix><text>\" }.");
  private final String prefix;

  public EchoTool() {
    this(null);
  }

  @ToolConstructor
  public EchoTool(
      @ToolSchema(
              name = "prefix",
              description = "Prefix to add to the echoed message",
              optional = true)
          final String prefix) {
    super(DESCRIPTOR);
    this.prefix = prefix;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(
              name = "text",
              description = "The string to return verbatim. Null is treated as empty string.")
          final String text,
      @ToolSchema(
              name = "prefix",
              description =
                  "String to prepend before the text. Overrides the tool's default prefix for this call. "
                      + "If null or blank, no prefix is added.",
              optional = true)
          final String prefix) {
    final String resolvedPrefix = StringUtils.isNotBlank(prefix) ? prefix : this.prefix;
    final String resolvedText = text == null ? "" : text;
    final String combined = (resolvedPrefix == null ? "" : resolvedPrefix) + resolvedText;
    return ToolOutput.direct(Map.of("output", combined));
  }
}
