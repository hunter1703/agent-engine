package com.agentengine.engine.tools;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.ToolCall;
import java.util.Map;

public final class DefaultToolHandler implements ToolHandler {
  private final Tool tool;

  public DefaultToolHandler(final Tool tool) {
    this.tool = tool;
  }

  @Override
  public String name() {
    return tool.name();
  }

  @Override
  public boolean isMutating() {
    return tool.isMutating();
  }

  @Override
  public boolean supportsParallel() {
    return tool.supportsParallel();
  }

  @Override
  public ToolResult handle(final ToolCall toolCall, final ToolContext context) {
    try {
      final Map<String, Object> args = toolCall == null ? Map.of() : toolCall.args();
      return tool.executeWithContext(context, args == null ? Map.of() : args);
    } catch (Exception ex) {
      return ToolResult.error(STR."Tool error in \{tool.name()}: \{ex.getMessage()}");
    }
  }
}
