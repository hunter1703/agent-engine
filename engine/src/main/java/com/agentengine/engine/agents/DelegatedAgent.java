package com.agentengine.engine.agents;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;

/**
 * Engine wrapper over ADK {@link BaseAgent} with config and optional delegation support.
 *
 * <p>Runtime behavior is forwarded to the delegated agent. Subclasses may override
 * {@link #runAsyncImpl} and {@link #runLiveImpl} directly instead.
 */
public class DelegatedAgent extends Agent {
  private final BaseAgent delegated;

  public DelegatedAgent(final BaseAgent delegated, final BaseAgentConfig agentConfig) {
    super(delegated.name(), delegated.description(), delegated.subAgents(), agentConfig, delegated.beforeAgentCallback(), delegated.afterAgentCallback());
    this.delegated = delegated;
  }

  @Override
  public List<? extends BaseAgent> subAgents() {
    return delegated != null ? delegated.subAgents() : super.subAgents();
  }

  @Override
  protected Flowable<Event> runAsyncImpl(final InvocationContext invocationContext) {
    if (delegated == null) {
      throw new IllegalStateException("No delegated agent present; override runAsyncImpl in subclass.");
    }
    return delegated.runAsync(invocationContext);
  }

  @Override
  protected Flowable<Event> runLiveImpl(final InvocationContext invocationContext) {
    if (delegated == null) {
      throw new IllegalStateException("No delegated agent present; override runLiveImpl in subclass.");
    }
    return delegated.runLive(invocationContext);
  }
}
