package com.agentengine.engine.tools;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.exception.ToolExecutionException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

public final class ToolCallRuntime {
  private final Semaphore mutatingGate = new Semaphore(1);
  private final ConcurrentMap<String, Semaphore> toolGates = new ConcurrentHashMap<>();

  public ToolOutput execute(final ToolHandler handler, final ToolCall call, final ToolContext context) {
    if (handler == null) {
      return ToolOutput.unknown(call);
    }
    final Semaphore toolGate = handler.supportsParallel() ? null : toolGates
        .computeIfAbsent(handler.name(), _ -> new Semaphore(1));
    acquireGate(handler.isMutating() ? mutatingGate : null);
    acquireGate(toolGate);
    final Instant start = Instant.now();
    try {
      final ToolResult result = handler.handle(call, context);
      final String status = result == null ? "error" : result.status();
      final String output = result == null ? "Tool returned no result" : result.output();
      return new ToolOutput(call, status, output, start, Instant.now().toEpochMilli() - start.toEpochMilli());
    } catch (Exception ex) {
      return new ToolOutput(call, "error", STR."Tool error in \{handler.name()}: \{ex.getMessage()}", start,
          Instant.now().toEpochMilli() - start.toEpochMilli());
    } finally {
      releaseGate(toolGate);
      releaseGate(handler.isMutating() ? mutatingGate : null);
    }
  }

  private void acquireGate(final Semaphore gate) {
    if (gate == null) {
      return;
    }
    try {
      gate.acquire();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ToolExecutionException("tool-runtime", "Tool execution interrupted", ex);
    }
  }

  private void releaseGate(final Semaphore gate) {
    if (gate != null) {
      gate.release();
    }
  }
}
