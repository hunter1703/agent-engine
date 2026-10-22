package com.agentengine.agent.infra.agents;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;

/** Engine base agent — carries {@link BaseAgentConfig} and delegates runtime to subclasses. */
public abstract class Agent extends BaseAgent implements ConfiguredAgent {
  private final BaseAgentConfig agentConfig;

  protected Agent(Builder<?, ?> builder) {
    this(
        builder.name(),
        builder.description(),
        builder.subAgents(),
        builder.agentConfig());
  }

  protected Agent(
      String name,
      String description,
      List<? extends BaseAgent> subAgents,
      BaseAgentConfig agentConfig) {
    super(name, description, subAgents, List.of(), List.of());
    this.agentConfig = agentConfig;
  }

  @Override
  public BaseAgentConfig getAgentConfig() {
    return agentConfig;
  }

  @Override
  public Flowable<Event> runAsync(final InvocationContext invocationContext) {
    return actingAsThisAgent(() -> super.runAsync(invocationContext));
  }

  public abstract static class Builder<B extends Builder<?, ?>, A extends BaseAgent> {
    private BaseAgentConfig agentConfig;
    private List<? extends BaseAgent> subAgents = List.of();
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
    public B subAgents(List<? extends BaseAgent> subAgents) {
      this.subAgents = subAgents;
      return (B) this;
    }

    public List<? extends BaseAgent> subAgents() {
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

    public abstract A build();
  }
}
