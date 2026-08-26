package com.agentengine.agent.infra.agents;

import com.agentengine.agent.infra.agents.flow.BaseFlow;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import io.reactivex.rxjava3.core.Completable;

public final class LLMAgent extends LlmAgent {
  private final Runnable closeHook;

  public LLMAgent(final Builder builder, final Runnable closeHook) {
    super(builder);
    this.closeHook = closeHook;
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    return new BaseFlow(maxSteps().orElse(null));
  }

  @Override
  public Completable close() {
    return super.close().doOnComplete(closeHook::run);
  }
}
