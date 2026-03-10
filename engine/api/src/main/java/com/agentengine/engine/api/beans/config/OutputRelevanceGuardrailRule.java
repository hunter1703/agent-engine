package com.agentengine.engine.api.beans.config;

import com.agentengine.util.builder.annotations.UiConditionOperator;
import com.agentengine.util.builder.annotations.UiField;
import com.agentengine.util.builder.annotations.UiNumber;
import com.agentengine.util.builder.annotations.UiRule;
import com.agentengine.util.builder.annotations.UiRuleEffect;
import com.agentengine.util.builder.annotations.UiSelect;
import com.agentengine.util.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("RELEVANCE")
@BsonDiscriminator(value = "RELEVANCE")
public class OutputRelevanceGuardrailRule extends GuardrailRule {

  @UiField(label = "Evaluator Model ID", order = 10)
  @UiText
  private String evaluatorModelId;

  @UiField(label = "Mode", order = 20)
  @UiSelect
  private RelevanceMode mode = RelevanceMode.STEER_THEN_BLOCK;

  @UiField(label = "Max Steering Retries", order = 30)
  @UiNumber
  @UiRule(
      effect = UiRuleEffect.VISIBLE,
      field = "mode",
      operator = UiConditionOperator.NOT_IN,
      values = {"STEER_ONLY"})
  private int maxSteeringRetries = 3;

  @UiField(label = "Relevance Threshold", order = 40)
  @UiNumber
  private double relevanceThreshold = 0.2;

  @UiField(label = "Anchor Strategy", order = 50)
  @UiSelect
  private RelevanceAnchorStrategy anchorStrategy = RelevanceAnchorStrategy.LATEST_USER_AND_PLAN;

  @UiField(label = "Recency", order = 60)
  @UiNumber
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "anchorStrategy", values = {"RECENT_USER"})
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
