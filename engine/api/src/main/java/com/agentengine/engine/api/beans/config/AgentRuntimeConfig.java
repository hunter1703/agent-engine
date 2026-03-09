package com.agentengine.engine.api.beans.config;

public class AgentRuntimeConfig {
  private boolean resumable = true;
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
