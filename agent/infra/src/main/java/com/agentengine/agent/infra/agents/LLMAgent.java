package com.agentengine.agent.infra.agents;

import com.agentengine.agent.infra.agents.flow.BaseFlow;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.agentengine.agent.infra.compaction.EventCompactor;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

public final class LLMAgent extends LlmAgent implements ConfiguredAgent {
  private final BaseAgentConfig agentConfig;
  private final Runnable closeHook;
  private final EventCompactor compactor;

  public LLMAgent(
      final Builder builder,
      final BaseAgentConfig agentConfig,
      final Runnable closeHook,
      final EventCompactor compactor) {
    super(builder);
    this.agentConfig = agentConfig;
    this.closeHook = closeHook;
    this.compactor = compactor;
  }

  @Override
  public BaseAgentConfig getAgentConfig() {
    return agentConfig;
  }

  public EventCompactor getCompactor() {
    return compactor;
  }

  @Override
  public Flowable<Event> runAsync(final InvocationContext invocationContext) {
    return actingAsThisAgent(() -> super.runAsync(invocationContext));
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    // LlmAgent builds its flow while it is being constructed, before this class's fields are set,
    // so the compactor is looked up each time the flow compacts.
    return new BaseFlow(
        maxSteps().orElse(null), (context, request) -> compactor.compact(context, request));
  }

  @Override
  public Completable close() {
    return super.close().doFinally(closeHook::run);
  }
}
