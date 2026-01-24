package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "hybrid")
@BsonDiscriminator(value = "hybrid")
public final class HybridAgentConfig extends AgentConfig {
  private AgentModelConfig routerModel;
  private AgentModelConfig planningModel;

  public HybridAgentConfig() {
    super(AgentType.HYBRID);
  }

  public AgentModelConfig getRouterModel() {
    return routerModel;
  }

  public void setRouterModel(final AgentModelConfig routerModel) {
    this.routerModel = routerModel;
  }

  public AgentModelConfig getPlanningModel() {
    return planningModel;
  }

  public void setPlanningModel(final AgentModelConfig planningModel) {
    this.planningModel = planningModel;
  }

  @Override
  public void validate() {
    routerModel.validate();
    planningModel.validate();
    super.validate();
  }
}
