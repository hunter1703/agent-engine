package com.agentengine.engine.api.agui;

import com.agui.core.event.ThinkingTextMessageContentEvent;

public class ThinkingChunkEvent extends ThinkingTextMessageContentEvent {
  private String delta;

  public String getDelta() {
    return delta;
  }

  public void setDelta(final String delta) {
    this.delta = delta;
  }
}
