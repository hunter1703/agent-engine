package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DefaultToolExecutor implements ToolExecutor {
  private final ToolRouter router;

  public DefaultToolExecutor(final List<ToolHandler> handlers) {
    this(new ToolRouter(handlers, new ToolCallRuntime()));
  }

  public DefaultToolExecutor(final ToolRouter router) {
    this.router = router;
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

      final ToolOutput output = router.dispatch(call, new ToolContext(sessionId));
      listener.onToolCallResult(sessionId, call.id(), output.output());
      final ToolExecution execution = output.toExecution();
      execution.setId(UUID.randomUUID().toString().replaceAll("-", ""));
      executions.add(execution);
    }
    return executions;
  }
}
