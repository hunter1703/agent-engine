package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.beans.NamedEntity;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;

public class AgentConfig extends NamedEntity implements Config {
  private String type;
  private String description;
  private String avatar;
  @NotNull private AgentModelConfig model;
  private SessionServiceConfig sessionStore = new MongoSessionServiceConfig();

  public AgentConfig() {
    this.type = AgentType.DEFAULT.name().toLowerCase();
  }

  public AgentConfig(AgentType agentType) {
    this.type = agentType.name().toLowerCase();
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

  public enum AgentType {
    UNKNOWN,
    DEFAULT,
    STORY;

    public static AgentType valueOfOrDefault(final String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      try {
        return AgentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }
}
