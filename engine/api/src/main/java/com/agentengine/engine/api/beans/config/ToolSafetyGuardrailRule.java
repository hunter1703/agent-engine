package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.tools.ToolRiskLevel;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("TOOL_SAFETY")
@BsonDiscriminator(value = "TOOL_SAFETY")
public class ToolSafetyGuardrailRule extends GuardrailRule {
  private ToolRiskLevel minToolRisk = ToolRiskLevel.HIGH;
  private List<String> toolNames = new ArrayList<>();

  public ToolSafetyGuardrailRule() {
    super(GuardrailRuleType.TOOL_SAFETY);
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
