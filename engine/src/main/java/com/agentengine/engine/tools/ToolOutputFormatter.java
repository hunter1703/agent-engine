package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.utils.JsonUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolOutputFormatter {
  private ToolOutputFormatter() {
  }

  public static String format(final List<ToolExecution> executions) {
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
}
