package com.agentengine.engine.tools;

import com.agentengine.engine.api.utils.JsonUtils;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.FunctionDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class ToolUtils {

  private static final Logger LOG = LoggerFactory.getLogger(ToolUtils.class);
  private static final boolean TOOL_DEBUG = Boolean.getBoolean("agent.engine.tool.debug")
      || Boolean.parseBoolean(System.getenv("AGENT_ENGINE_TOOL_DEBUG"));

  private ToolUtils() {
  }

  public static String buildToolMessage(final List<BaseTool> tools) {
    if (tools == null || tools.isEmpty()) {
      return "";
    }
    final StringBuilder builder = new StringBuilder("<AVAILABLE_TOOLS>\n");
    for (BaseTool tool : tools) {
      if (tool == null || tool.name() == null || tool.name().isBlank()) {
        continue;
      }
      String line = STR."- \{tool.name()}";
      if (tool.description() != null && !tool.description().isBlank()) {
        line += STR." - \{tool.description()}";
      }
      builder.append(line).append("\n\t-").append("tool args schema - ")
          .append(
              JsonUtils.toJson(tool.declaration().orElse(FunctionDeclaration.builder().build()).parametersJsonSchema()))
          .append("\n");
    }
    builder.append("</AVAILABLE_TOOLS>");
    return builder.toString();
  }

  public static void logToolInstructions(final String agentId, final String toolInstructions) {
    if (!TOOL_DEBUG) {
      return;
    }
    final String resolvedAgentId = agentId == null ? "unknown" : agentId;
    if (toolInstructions == null || toolInstructions.isBlank()) {
      LOG.info("No tool instructions for agent {}", resolvedAgentId);
      return;
    }
    LOG.info("Tool instructions for agent {}:\n{}", resolvedAgentId, toolInstructions);
  }
}
