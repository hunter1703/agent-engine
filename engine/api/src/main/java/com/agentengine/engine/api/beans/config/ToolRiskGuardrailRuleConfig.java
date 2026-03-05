package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("TOOL_RISK")
@BsonDiscriminator(value = "TOOL_RISK")
public class ToolRiskGuardrailRuleConfig extends GuardrailRuleConfig {
  private ToolRiskLevel minToolRisk = ToolRiskLevel.HIGH;
  private List<String> toolNames = new ArrayList<>();

  public ToolRiskGuardrailRuleConfig() {
    super(GuardrailRuleType.TOOL_RISK);
    setStage(GuardrailStage.TOOL);
  }

  public ToolRiskLevel getMinToolRisk() {
    return minToolRisk;
  }

  public void setMinToolRisk(final ToolRiskLevel minToolRisk) {
    this.minToolRisk = minToolRisk == null ? ToolRiskLevel.HIGH : minToolRisk;
  }

  public List<String> getToolNames() {
    return toolNames;
  }

  public void setToolNames(final List<String> toolNames) {
    this.toolNames = toolNames == null ? new ArrayList<>() : new ArrayList<>(toolNames);
  }
}
