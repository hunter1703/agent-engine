package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.Tool;
import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.exception.ToolExecutionException;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public final class DefaultToolExecutor implements ToolExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultToolExecutor.class);
  private final Map<String, Tool> toolByName;
  private final Semaphore mutatingGate = new Semaphore(1);
  private final Map<String, Semaphore> toolGates = new ConcurrentHashMap<>();

  public DefaultToolExecutor(final List<Tool> tools) {
    final Map<String, Tool> toolMap = new ConcurrentHashMap<>();
    for (final Tool tool : CollectionUtils.nullSafeList(tools)) {
      toolMap.put(tool.name(), tool);
    }
    this.toolByName = toolMap;
  }

  @Override
  public List<ToolExecution> execute(final String sessionId, final String runId, final List<ToolCall> toolCalls,
      final AgentListener listener) {
    if (CollectionUtils.isEmpty(toolCalls)) {
      return List.of();
    }

    final List<ToolExecution> executions = new ArrayList<>();
    for (ToolCall call : toolCalls) {
      if (call == null) {
        continue;
      }
      listener.onToolCallStart(sessionId, call.id(), call.name());
      if (call.args() != null) {
        listener.onToolCallArgs(sessionId, call.id(), JsonUtils.toJson(call.args()));
      }
      listener.onToolCallEnd(sessionId, call.id());

      final ToolExecution execution = executeTool(call, new ToolContext(sessionId));
      listener.onToolCallResult(sessionId, call.id(), execution.getOutput());
      execution.setId(UUID.randomUUID().toString().replaceAll("-", ""));
      executions.add(execution);
    }
    return executions;
  }

  private ToolExecution executeTool(final ToolCall call, final ToolContext context) {
        final Tool tool = toolByName.get(call.name());
        final Instant start = Instant.now();

        if (tool == null) {
            return createToolExecution(call, "unknown", STR."Unknown tool: \{call.name()}", start);
        }

        // Acquire appropriate locks based on tool properties
        final Semaphore toolGate = tool.supportsParallel() ? null : toolGates.computeIfAbsent(
                tool.name(), _ -> new Semaphore(1));

        acquireGate(tool.isMutating() ? mutatingGate : null);
        acquireGate(toolGate);

        try {
            final ToolResult result = tool.executeWithContext(context, call.args());
            final String status = result == null ? "error" : result.status();
            final String output = result == null ? "Tool returned no result" : result.output();

            return createToolExecution(call, status, output, start);
        } catch (Exception ex) {
            return createToolExecution(call, "error", "Tool error in " + tool.name() + ": " + ex.getMessage(), start);
        } finally {
            releaseGate(toolGate);
            releaseGate(tool.isMutating() ? mutatingGate : null);
        }
    }

  private ToolExecution createToolExecution(final ToolCall call, final String status, final String output,
      final Instant start) {
    final Instant end = Instant.now();
    return new ToolExecution(call, status, output, start, end.toEpochMilli() - start.toEpochMilli());
  }

  private void acquireGate(final Semaphore gate) {
    if (gate == null) {
      return;
    }
    try {
      gate.acquire();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ToolExecutionException("tool-executor", "Tool execution interrupted", ex);
    }
  }

  private void releaseGate(final Semaphore gate) {
    if (gate != null) {
      gate.release();
    }
  }
}
