package com.agentengine.engine.api.beans.config;

public class TopicControlConfig {
  private boolean enabled = true;
  private OnTopicMode mode = OnTopicMode.STEER_THEN_BLOCK;
  private int maxSteeringRetries = 3;
  private double relevanceThreshold = 0.2;
  private TopicAnchorStrategy anchorStrategy = TopicAnchorStrategy.LATEST_USER_AND_PLAN;
  private int recency = 5;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public OnTopicMode getMode() {
    return mode;
  }

  public void setMode(final OnTopicMode mode) {
    this.mode = mode;
  }

  public int getMaxSteeringRetries() {
    return maxSteeringRetries;
  }

  public void setMaxSteeringRetries(final int maxSteeringRetries) {
    this.maxSteeringRetries = maxSteeringRetries;
  }

  public double getRelevanceThreshold() {
    return relevanceThreshold;
  }

  public void setRelevanceThreshold(final double relevanceThreshold) {
    this.relevanceThreshold = relevanceThreshold;
  }

  public TopicAnchorStrategy getAnchorStrategy() {
    return anchorStrategy;
  }

  public void setAnchorStrategy(final TopicAnchorStrategy anchorStrategy) {
    this.anchorStrategy = anchorStrategy;
  }

  public int getRecency() {
    return recency;
  }

  public void setRecency(final int recency) {
    this.recency = recency;
  }
}
