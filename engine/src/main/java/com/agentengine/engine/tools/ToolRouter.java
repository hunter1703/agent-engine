package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.utils.CollectionUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolRouter {
  private final Map<String, ToolHandler> handlers;
  private final ToolCallRuntime runtime;

  public ToolRouter(final List<ToolHandler> handlers, final ToolCallRuntime runtime) {
    final Map<String, ToolHandler> handlerMap = new HashMap<>();
    for (ToolHandler handler : CollectionUtils.nullSafeList(handlers)) {
      if (handler != null && handler.name() != null && !handler.name().isBlank()) {
        handlerMap.put(handler.name(), handler);
      }
    }
    this.handlers = Map.copyOf(handlerMap);
    this.runtime = runtime;
  }

  public ToolOutput dispatch(final ToolCall call, final ToolContext context) {
    if (call == null || call.name() == null) {
      return ToolOutput.unknown(call);
    }
    final ToolHandler handler = handlers.get(call.name());
    if (handler == null) {
      return ToolOutput.unknown(call);
    }
    return runtime.execute(handler, call, context);
  }
}
