package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("orchestrator")
@BsonDiscriminator(value = "orchestrator")
public class OrchestratorAgentConfig extends BaseAgentConfig {
  private OrchestrationMode orchestrationMode = OrchestrationMode.TRANSFER;
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
