package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.utils.StringUtils;
import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlsoDefault = AgentConfig.class)
@BsonDiscriminator(key = "type", value = "default")
public class AgentConfig implements Config {
  private String type;
  private String agentId;
  private String name;
  private String description;
  private String avatar;
  private AgentModelConfig model;
  private SessionServiceConfig sessionStore = new InMemorySessionServiceConfig();

  public AgentConfig() {
    this.type = AgentType.DEFAULT.name().toLowerCase();
  }

  public AgentConfig(AgentType agentType) {
    this.type = agentType.name().toLowerCase();
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(final String avatar) {
    this.avatar = avatar;
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

  public SessionServiceConfig getSessionStore() {
    return sessionStore;
  }

  public void setSessionStore(final SessionServiceConfig sessionStore) {
    this.sessionStore = sessionStore;
  }

  @Override
  public void validate() {
    if (StringUtils.isBlank(agentId)) {
      throw new IllegalArgumentException("name is required");
    }
    if (model == null) {
      throw new IllegalArgumentException("model is required");
    }
    model.validate();
    sessionStore.validate();
  }

  public enum AgentType {
    DEFAULT
  }
}
