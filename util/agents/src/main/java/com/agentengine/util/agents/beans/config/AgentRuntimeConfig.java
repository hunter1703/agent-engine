package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiBoolean;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiNumber;

public class AgentRuntimeConfig {
  @UiField(label = "Resumable", order = 10)
  @UiBoolean
  private boolean resumable = true;

  @UiField(label = "Max Steps", order = 20)
  @UiNumber
  private int maxSteps = 50;

  public boolean isResumable() {
    return resumable;
  }

  public void setResumable(final boolean resumable) {
    this.resumable = resumable;
  }

  public int getMaxSteps() {
    return maxSteps;
  }

  public void setMaxSteps(final int maxSteps) {
    this.maxSteps = maxSteps <= 0 ? 50 : maxSteps;
  }
}
