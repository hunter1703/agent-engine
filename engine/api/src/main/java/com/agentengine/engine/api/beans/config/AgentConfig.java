package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.utils.StringUtils;
import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlso = {HybridAgentConfig.class})
@BsonDiscriminator(key = "type")
public class AgentConfig implements Config {
  private String type;
  private String name;
  private AgentModelConfig model;
  private SessionStoreConfig sessionStore = new InMemorySessionStoreConfig();

  public AgentConfig() {
    this.type = AgentType.DEFAULT.name().toLowerCase();
  }

  public AgentConfig(AgentType agentType) {
    this.type = agentType.name().toLowerCase();
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public AgentModelConfig getModel() {
    return model;
  }

  public void setModel(final AgentModelConfig model) {
    this.model = model;
  }

  public SessionStoreConfig getSessionStore() {
    return sessionStore;
  }

  public void setSessionStore(final SessionStoreConfig sessionStore) {
    this.sessionStore = sessionStore;
  }

  @Override
  public void validate() {
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("name is required");
    }

    model.validate();
    sessionStore.validate();
  }

  public static AgentConfig empty() {
    return new HybridAgentConfig();
  }

  public enum AgentType {
    DEFAULT, HYBRID
  }
}
