package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiConditionOperator;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiLookup;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.builder.annotations.UiRule;
import com.agentengine.util.common.builder.annotations.UiRuleEffect;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("RELEVANCE")
@BsonDiscriminator(value = "RELEVANCE")
public class OutputRelevanceGuardrailRule extends GuardrailRule {
  private static final String DEFAULT_MODE = RelevanceMode.STEER_THEN_BLOCK.name();
  private static final String DEFAULT_ANCHOR_STRATEGY = RelevanceAnchorStrategy.LATEST_USER_AND_PLAN.name();

  @UiField(label = "Evaluator Model ID", order = 10)
  @UiLookup(assetType = "model")
  private String evaluatorModelId;

  @UiField(label = "Mode", order = 20)
  @UiSelect(enumType = RelevanceMode.class)
  private String mode = DEFAULT_MODE;

  @UiField(label = "Max Steering Retries", order = 30)
  @UiNumber
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "mode", operator = UiConditionOperator.NOT_IN, values = {"STEER_ONLY"})
  private int maxSteeringRetries = 3;

  @UiField(label = "Relevance Threshold", order = 40)
  @UiNumber
  private double relevanceThreshold = 0.2;

  @UiField(label = "Anchor Strategy", order = 50)
  @UiSelect(enumType = RelevanceAnchorStrategy.class)
  private String anchorStrategy = DEFAULT_ANCHOR_STRATEGY;

  @UiField(label = "Recency", order = 60)
  @UiNumber
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "anchorStrategy", values = {"RECENT_USER"})
  private int recency = 5;

  public OutputRelevanceGuardrailRule() {
    super(GuardrailRuleType.RELEVANCE);
    setStage(GuardrailStage.OUTPUT.name());
  }

  public String getEvaluatorModelId() {
    return evaluatorModelId;
  }

  public void setEvaluatorModelId(final String evaluatorModelId) {
    this.evaluatorModelId = evaluatorModelId;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(final String mode) {
    this.mode = mode == null || mode.isBlank() ? DEFAULT_MODE : mode;
  }

  public RelevanceMode modeEnum() {
    return RelevanceMode.valueOfOrDefault(mode);
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

  public String getAnchorStrategy() {
    return anchorStrategy;
  }

  public void setAnchorStrategy(final String anchorStrategy) {
    this.anchorStrategy = anchorStrategy == null || anchorStrategy.isBlank() ? DEFAULT_ANCHOR_STRATEGY : anchorStrategy;
  }

  public RelevanceAnchorStrategy anchorStrategyEnum() {
    return RelevanceAnchorStrategy.valueOfOrDefault(anchorStrategy);
  }

  public int getRecency() {
    return recency;
  }

  public void setRecency(final int recency) {
    this.recency = recency;
  }
}
