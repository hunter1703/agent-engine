package com.agentengine.agent.infra.agents;

import com.agentengine.agent.infra.agents.flow.BaseFlow;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.summarizer.EventCompactor;
import io.reactivex.rxjava3.core.Completable;

public final class LLMAgent extends LlmAgent {
  private final Runnable closeHook;
  private final EventCompactor compactor;

  public LLMAgent(final Builder builder, final Runnable closeHook, final EventCompactor compactor) {
    super(builder);
    this.closeHook = closeHook;
    this.compactor = compactor;
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    return new BaseFlow(maxSteps().orElse(null), compactor);
  }

  @Override
  public Completable close() {
    return super.close().doOnComplete(closeHook::run);
  }
}
