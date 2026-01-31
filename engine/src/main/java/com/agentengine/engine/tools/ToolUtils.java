package com.agentengine.engine.tools;

import com.agentengine.engine.api.utils.JsonUtils;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.FunctionDeclaration;

import java.util.List;

public final class ToolUtils {

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
      String line = "- " + tool.name();
      if (tool.description() != null && !tool.description().isBlank()) {
        line += " - " + tool.description();
      }
      builder.append(line).append("\n\t-").append("tool args schema - ")
          .append(
              JsonUtils.toJson(tool.declaration().orElse(FunctionDeclaration.builder().build()).parametersJsonSchema()))
          .append("\n");
    }
    builder.append("</AVAILABLE_TOOLS>");
    return builder.toString();
  }
}
