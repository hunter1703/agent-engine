package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiLookup;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("TOOL_SAFETY")
@BsonDiscriminator(value = "TOOL_SAFETY")
public class ToolSafetyGuardrailRule extends GuardrailRule {
  private static final String DEFAULT_MIN_TOOL_RISK = ToolRiskLevel.HIGH.name();

  @UiField(label = "Minimum Tool Risk", order = 10)
  @UiSelect(enumType = ToolRiskLevel.class)
  private String minToolRisk = DEFAULT_MIN_TOOL_RISK;

  @UiField(label = "Restricted Tools", order = 20)
  @UiLookup(assetType = "tool")
  private List<String> toolNames = new ArrayList<>();

  public ToolSafetyGuardrailRule() {
    super(GuardrailRuleType.TOOL_SAFETY);
    setStage(GuardrailStage.TOOL.name());
  }

  public String getMinToolRisk() {
    return minToolRisk;
  }

  public void setMinToolRisk(final String minToolRisk) {
    this.minToolRisk = minToolRisk == null || minToolRisk.isBlank() ? DEFAULT_MIN_TOOL_RISK : minToolRisk;
  }

  public ToolRiskLevel minToolRiskEnum() {
    return ToolRiskLevel.valueOfOrDefault(minToolRisk);
  }

  public List<String> getToolNames() {
    return toolNames;
  }

  public void setToolNames(final List<String> toolNames) {
    this.toolNames = toolNames == null ? new ArrayList<>() : new ArrayList<>(toolNames);
  }
}
