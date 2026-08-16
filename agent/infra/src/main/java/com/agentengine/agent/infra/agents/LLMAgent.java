package com.agentengine.agent.infra.agents;

import com.agentengine.agent.infra.agents.flow.BaseFlow;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.tools.BaseTool;
import io.reactivex.rxjava3.core.Completable;

public final class LLMAgent extends LlmAgent {
  private final Runnable closeHook;

  public LLMAgent(final Builder builder, final Runnable closeHook) {
    super(builder);
    this.closeHook = closeHook;
  }

  @Override
  protected BaseLlmFlow determineLlmFlow() {
    return new BaseFlow(
        maxSteps().orElse(null), tools().blockingGet().stream().map(BaseTool::name).toList());
  }

  @Override
  public Completable close() {
    return super.close().doOnComplete(closeHook::run);
  }
}
