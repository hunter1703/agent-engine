package com.agentengine.engine.tools;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.Tool;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class ToolUtils {
  private static final Logger LOG = Logger.getLogger(ToolUtils.class.getName());
  private ToolUtils() {
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
      builder.append(line).append("\n\t-").append("tool args schema - ").append(JsonUtils.toJson(tool.parametersSchema())).append("\n");
    }
    builder.append("</AVAILABLE_TOOLS>");
    return builder.toString();
  }

  public static String formatToolExecutions(final List<ToolExecution> executions) {
    final List<Map<String, Object>> results = new ArrayList<>();
    for (ToolExecution execution : executions) {
      final ToolCall call = execution == null ? null : execution.getToolCall();
      final Map<String, Object> entry = new HashMap<>();
      entry.put("id", call == null ? null : call.id());
      entry.put("name", call == null ? null : call.name());
      entry.put("args", call == null ? null : call.args());
      entry.put("status", execution == null ? null : execution.getStatus());
      entry.put("output", execution == null ? null : execution.getOutput());
      entry.put("duration_ms", execution == null ? null : execution.getDurationMs());
      results.add(entry);
    }
    return JsonUtils.toJson(Map.of("tool_calls", results));
  }

  public static List<ToolSpecification> buildToolSpecifications(final List<Tool> tools) {
    if (CollectionUtils.isEmpty(tools)) {
      return List.of();
    }
    final List<ToolSpecification> specifications = new ArrayList<>();
    for (Tool tool : tools) {
      if (tool == null || StringUtils.isBlank(tool.name())) {
        continue;
      }
      final JsonObjectSchema parameters = JsonObjectSchema.builder().additionalProperties(true).build();
      specifications.add(ToolSpecification.builder().name(tool.name())
          .description(tool.description() == null ? "" : tool.description()).parameters(parameters).build());
    }
    return specifications;
  }
}
