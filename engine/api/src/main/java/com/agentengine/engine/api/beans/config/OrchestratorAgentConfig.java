package com.agentengine.engine.api.beans.config;

import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiRule;
import com.agentengine.util.common.builder.annotations.UiRuleEffect;
import com.agentengine.util.common.builder.annotations.UiSelect;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("ORCHESTRATOR")
@BsonDiscriminator(value = "ORCHESTRATOR")
public class OrchestratorAgentConfig extends BaseAgentConfig {
  private static final String DEFAULT_ORCHESTRATION_MODE = OrchestrationMode.TRANSFER.name();

  @UiField(label = "Orchestration Mode", step = "identity", section = "identity", order = 70)
  @UiSelect(enumType = OrchestrationMode.class)
  private String orchestrationMode = DEFAULT_ORCHESTRATION_MODE;

  @UiField(label = "Parallel Orchestration", step = "identity", section = "identity", order = 80)
  @UiRule(
      effect = UiRuleEffect.VISIBLE,
      field = "orchestrationMode",
      values = {"PARALLEL"})
  private OrchestratorParallelConfig parallel = new OrchestratorParallelConfig();

  public OrchestratorAgentConfig() {
    super(AgentType.ORCHESTRATOR);
  }

  public String getOrchestrationMode() {
    return orchestrationMode;
  }

  public void setOrchestrationMode(final String orchestrationMode) {
    this.orchestrationMode =
        orchestrationMode == null || orchestrationMode.isBlank()
            ? DEFAULT_ORCHESTRATION_MODE
            : orchestrationMode;
  }

  public OrchestrationMode orchestrationModeEnum() {
    return OrchestrationMode.valueOfOrDefault(orchestrationMode);
  }

  public OrchestratorParallelConfig getParallel() {
    return parallel;
  }

  public void setParallel(final OrchestratorParallelConfig parallel) {
    this.parallel = parallel == null ? new OrchestratorParallelConfig() : parallel;
  }
}
