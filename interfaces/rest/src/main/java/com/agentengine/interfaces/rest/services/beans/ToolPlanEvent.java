package com.agentengine.interfaces.rest.services.beans;

import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolRequest;
import com.agui.core.event.CustomEvent;

import java.util.Collection;
import java.util.List;

public class ToolPlanEvent extends CustomEvent {
  private Collection<ToolCall> toolCalls;

  public ToolPlanEvent() {
  }

  public ToolPlanEvent(final Collection<ToolCall> toolCalls) {
    this.toolCalls = toolCalls;
  }

  public Collection<ToolCall> getToolCalls() {
    return toolCalls;
  }

  public void setToolCalls(final Collection<ToolCall> toolCalls) {
    this.toolCalls = toolCalls;
  }
}
