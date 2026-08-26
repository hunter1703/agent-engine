package com.agentengine.agent.infra.agents;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks;
import java.util.Collections;
import java.util.List;

/** Engine base agent — carries {@link BaseAgentConfig} and delegates runtime to subclasses. */
public abstract class Agent extends BaseAgent {
  private final BaseAgentConfig agentConfig;

  protected Agent(Builder<?, ?> builder) {
    this(
        builder.name(),
        builder.description(),
        builder.subAgents(),
        builder.agentConfig(),
        builder.beforeAgentCallback(),
        builder.afterAgentCallback());
  }

  protected Agent(
      String name,
      String description,
      List<? extends BaseAgent> subAgents,
      BaseAgentConfig agentConfig,
      List<? extends Callbacks.BeforeAgentCallback> beforeAgentCallbacks,
      List<? extends Callbacks.AfterAgentCallback> afterAgentCallbacks) {
    super(name, description, subAgents, beforeAgentCallbacks, afterAgentCallbacks);
    this.agentConfig = agentConfig;
  }

  public BaseAgentConfig getAgentConfig() {
    return agentConfig;
  }

  public abstract static class Builder<B extends Builder<?, ?>, A extends Agent> {
    private BaseAgentConfig agentConfig;
    private List<? extends Agent> subAgents = List.of();
    private Runnable closeHook;

    public String name() {
      return agentConfig.getId();
    }

    public String description() {
      final String description = agentConfig.getDescription();
      return StringUtils.isNotBlank(description)
          ? description
          : "Agent with id: " + agentConfig.getId();
    }

    @SuppressWarnings("unchecked")
    public B subAgents(List<? extends Agent> subAgents) {
      this.subAgents = subAgents;
      return (B) this;
    }

    public List<? extends Agent> subAgents() {
      return subAgents;
    }

    @SuppressWarnings("unchecked")
    public B closeHook(Runnable closeHook) {
      this.closeHook = closeHook;
      return (B) this;
    }

    public Runnable closeHook() {
      return closeHook;
    }

    @SuppressWarnings("unchecked")
    public B agentConfig(BaseAgentConfig agentConfig) {
      this.agentConfig = agentConfig;
      return (B) this;
    }

    public BaseAgentConfig agentConfig() {
      return agentConfig;
    }

    @SuppressWarnings("unchecked")
    public B beforeAgentCallback(
        List<? extends Callbacks.BeforeAgentCallback> beforeAgentCallback) {
      return (B) this;
    }

    public List<? extends Callbacks.BeforeAgentCallback> beforeAgentCallback() {
      return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public B afterAgentCallback(List<? extends Callbacks.AfterAgentCallback> afterAgentCallback) {
      return (B) this;
    }

    public List<? extends Callbacks.AfterAgentCallback> afterAgentCallback() {
      return Collections.emptyList();
    }

    public abstract A build();
  }
}
