package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.beans.NamedEntity;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;

import com.agentengine.engine.api.beans.Secure;

public class AgentConfig extends NamedEntity implements Config {
  private String type;
  @Secure private String description;
  private String avatar;
  @NotNull private AgentModelConfig model;
  private String routingModelId;
  private int routingHistorySize = 1;
  private SessionServiceConfig sessionStore = new MongoSessionServiceConfig();
  private GuardrailsConfig guardrails = new GuardrailsConfig();
  private EvaluatorOptimizerConfig evaluatorOptimizer = new EvaluatorOptimizerConfig();

  public AgentConfig() {
    this.type = AgentType.DEFAULT.name().toLowerCase();
  }

  public AgentConfig(AgentType agentType) {
    this.type = agentType.name().toLowerCase();
  }

  @Override
  @Secure
  public String getName() {
    return super.getName();
  }

  @Override
  @Secure
  public void setName(String name) {
    super.setName(name);
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

  public String getRoutingModelId() {
    return routingModelId;
  }

  public void setRoutingModelId(final String routingModelId) {
    this.routingModelId = routingModelId;
  }

  public int getRoutingHistorySize() {
    return routingHistorySize;
  }

  public void setRoutingHistorySize(final int routingHistorySize) {
    this.routingHistorySize = routingHistorySize;
  }

  public SessionServiceConfig getSessionStore() {
    return sessionStore;
  }

  public void setSessionStore(final SessionServiceConfig sessionStore) {
    this.sessionStore = sessionStore;
  }

  public GuardrailsConfig getGuardrails() {
    return guardrails;
  }

  public void setGuardrails(final GuardrailsConfig guardrails) {
    this.guardrails = guardrails;
  }

  public enum AgentType {
    /** Fallback for invalid or missing config values. */
    UNKNOWN,
    /** Standard single-agent runtime with default flow/pipeline behavior. */
    DEFAULT,
    STORY;
    /** Story-specialized agent with routing + multi-phase story generation flow. */
    STORY,

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
