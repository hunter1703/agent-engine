package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("RELEVANCE")
@BsonDiscriminator(value = "RELEVANCE")
public class OutputRelevanceGuardrailRule extends GuardrailRule {
  private String evaluatorModelId;
  private RelevanceMode mode = RelevanceMode.STEER_THEN_BLOCK;
  private int maxSteeringRetries = 3;
  private double relevanceThreshold = 0.2;
  private RelevanceAnchorStrategy anchorStrategy = RelevanceAnchorStrategy.LATEST_USER_AND_PLAN;
  private int recency = 5;

  public OutputRelevanceGuardrailRule() {
    super(GuardrailRuleType.RELEVANCE);
    setStage(GuardrailStage.OUTPUT);
  }

  public String getEvaluatorModelId() {
    return evaluatorModelId;
  }

  public void setEvaluatorModelId(final String evaluatorModelId) {
    this.evaluatorModelId = evaluatorModelId;
  }

  public RelevanceMode getMode() {
    return mode;
  }

  public void setMode(final RelevanceMode mode) {
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

  public RelevanceAnchorStrategy getAnchorStrategy() {
    return anchorStrategy;
  }

  public void setAnchorStrategy(final RelevanceAnchorStrategy anchorStrategy) {
    this.anchorStrategy = anchorStrategy;
  }

  public int getRecency() {
    return recency;
  }

  public void setRecency(final int recency) {
    this.recency = recency;
  }
}
