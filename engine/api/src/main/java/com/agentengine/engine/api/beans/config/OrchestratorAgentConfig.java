package com.agentengine.engine.api.beans.config;

import com.agentengine.util.builder.annotations.UiField;
import com.agentengine.util.builder.annotations.UiRule;
import com.agentengine.util.builder.annotations.UiRuleEffect;
import com.agentengine.util.builder.annotations.UiSelect;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("orchestrator")
@BsonDiscriminator(value = "orchestrator")
public class OrchestratorAgentConfig extends BaseAgentConfig {

  @UiField(label = "Orchestration Mode", step = "identity", section = "identity", order = 70)
  @UiSelect
  private OrchestrationMode orchestrationMode = OrchestrationMode.TRANSFER;

  @UiField(label = "Parallel Orchestration", step = "identity", section = "identity", order = 80)
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "orchestrationMode", values = {"PARALLEL"})
  private OrchestratorParallelConfig parallel = new OrchestratorParallelConfig();

  public OrchestratorAgentConfig() {
    super(AgentType.ORCHESTRATOR);
  }

  public OrchestrationMode getOrchestrationMode() {
    return orchestrationMode;
  }

  public void setOrchestrationMode(final OrchestrationMode orchestrationMode) {
    this.orchestrationMode =
        orchestrationMode == null ? OrchestrationMode.TRANSFER : orchestrationMode;
  }

  public OrchestratorParallelConfig getParallel() {
    return parallel;
  }

  public void setParallel(final OrchestratorParallelConfig parallel) {
    this.parallel = parallel == null ? new OrchestratorParallelConfig() : parallel;
  }
}
