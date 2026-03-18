package com.agentengine.engine.api.agui;

import com.agui.core.event.CustomEvent;

public abstract class BaseCustomEvent extends CustomEvent {
  private final String eventType;

  protected BaseCustomEvent(final String eventType) {
    this.eventType = eventType;
  }

  public String getEventType() {
    return eventType;
  }
}
