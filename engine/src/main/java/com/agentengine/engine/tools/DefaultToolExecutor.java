package com.agentengine.engine.tools;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.CollectionUtils;
import java.time.Instant;
import java.util.*;

public final class DefaultToolExecutor implements ToolExecutor {
  private final Map<String, Tool> toolByName;

  public DefaultToolExecutor(final List<Tool> tools) {
    final Map<String, Tool> toolMap = new HashMap<>();
    for (final Tool tool : CollectionUtils.nullSafeList(tools)) {
      toolMap.put(tool.name(), tool);
    }
    this.toolByName = Collections.unmodifiableMap(toolMap);
  }

  @Override
  public List<ToolExecution> execute(final String sessionId, final String runId, final List<ToolCall> toolCalls,
      final AgentListener listener) {
    if (CollectionUtils.isEmpty(toolCalls)) {
      return List.of();
    }

    final List<ToolExecution> executions = new ArrayList<>();
    for (ToolCall call : toolCalls) {
      listener.onToolCallStart(sessionId, call.id(), call.name());
      if (call.args() != null) {
        listener.onToolCallArgs(sessionId, call.id(), JsonUtils.toJson(call.args()));
      }
      listener.onToolCallEnd(sessionId, call.id());

      final Tool tool = toolByName.get(call.name());
      final Instant start = Instant.now();
      String status = "ok";
      String output;
      if (tool == null) {
        status = "unknown";
        output = STR."Unknown tool: \{call.name()}";
      } else {
        try {
          final ToolContext context = new ToolContext(sessionId);
          final ToolResult result = tool.executeWithContext(context, call.args());
          output = result.output();
          status = result.status();
        } catch (Exception ex) {
          status = "error";
          output = STR."Tool error in \{call.name()}: \{ex.getMessage()}";
        }
      }
      listener.onToolCallResult(sessionId, call.id(), output);

      final Instant end = Instant.now();
      final ToolExecution toolExecution =
          new ToolExecution(call, status, output, start, end.toEpochMilli() - start.toEpochMilli());
      toolExecution.setId(UUID.randomUUID().toString().replaceAll("-", ""));
      executions.add(toolExecution);
    }
    return executions;
  }
}
