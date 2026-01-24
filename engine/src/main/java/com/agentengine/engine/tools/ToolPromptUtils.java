package com.agentengine.engine.tools;

import static java.lang.StringTemplate.STR;

import java.util.List;

public final class ToolPromptUtils {
  private ToolPromptUtils() {
  }

  public static String buildToolMessage(final List<Tool> tools) {
    if (tools == null || tools.isEmpty()) {
      return "";
    }
    final StringBuilder builder = new StringBuilder("<AVAILABLE_TOOLS>\n");
    for (Tool tool : tools) {
      if (tool == null || tool.name() == null || tool.name().isBlank()) {
        continue;
      }
      String line = STR."- \{tool.name()}";
      if (tool.description() != null && !tool.description().isBlank()) {
        line += STR." - \{tool.description()}";
      }
      builder.append(line).append("\n");
    }
    builder.append("</AVAILABLE_TOOLS>");
    return builder.toString();
  }
}
