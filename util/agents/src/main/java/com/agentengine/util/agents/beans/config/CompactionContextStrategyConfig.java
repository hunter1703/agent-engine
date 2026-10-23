package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiLookup;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.agentengine.util.common.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("DEFAULT")
@BsonDiscriminator(
    value = "com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig")
public class CompactionContextStrategyConfig extends ContextStrategyConfig {
  @UiField(label = "Threshold Unit", step = "model", section = "context", order = 30)
  @UiSelect(enumType = ThresholdUnit.class)
  private String thresholdUnit = ThresholdUnit.PERCENT.name();

  @UiField(label = "Trigger Threshold", step = "model", section = "context", order = 40)
  @UiNumber
  private double triggerThreshold = 95;

  @UiField(label = "Recency Threshold", step = "model", section = "context", order = 45)
  @UiNumber
  private double recencyThreshold = 10;

  @UiField(label = "Compaction Model ID", step = "model", section = "context", order = 50)
  @UiLookup(assetType = AssetClass.MODEL)
  private String modelId;

  @UiField(label = "Prompt Template", step = "model", section = "context", order = 60)
  @UiText(multiline = true, rows = 4)
  private String promptTemplate;

  public CompactionContextStrategyConfig() {
    super(ContextStrategyType.DEFAULT);
  }

  /** What the thresholds are measured in: tokens, or a percentage of the model's context length. */
  public String getThresholdUnit() {
    return thresholdUnit;
  }

  public void setThresholdUnit(final String thresholdUnit) {
    this.thresholdUnit = thresholdUnit;
  }

  public ThresholdUnit thresholdUnitEnum() {
    return ThresholdUnit.valueOfOrDefault(thresholdUnit);
  }

  /** The context size past which the history is compacted. */
  public double getTriggerThreshold() {
    return triggerThreshold;
  }

  public void setTriggerThreshold(final double triggerThreshold) {
    this.triggerThreshold = triggerThreshold;
  }

  /** The most the latest events may take when the history is compacted; the rest is summarized. */
  public double getRecencyThreshold() {
    return recencyThreshold;
  }

  public void setRecencyThreshold(final double recencyThreshold) {
    this.recencyThreshold = recencyThreshold;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(final String modelId) {
    this.modelId = modelId;
  }

  public String getPromptTemplate() {
    return promptTemplate;
  }

  public void setPromptTemplate(final String promptTemplate) {
    this.promptTemplate = promptTemplate;
  }

  public enum ThresholdUnit {
    UNKNOWN,
    PERCENT,
    TOKENS;

    public static ThresholdUnit valueOfOrDefault(final String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      try {
        return ThresholdUnit.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }
}
