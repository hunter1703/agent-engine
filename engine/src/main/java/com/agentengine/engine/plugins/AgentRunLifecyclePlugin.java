package com.agentengine.engine.plugins;

import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Initializes and clears per-run state for each agent invocation.
 */
public final class AgentRunLifecyclePlugin extends BasePlugin {
  private static final String NAME = "agent_run_lifecycle";

  public AgentRunLifecyclePlugin() {
    super(NAME);
  }

  @Override
  public Maybe<Content> beforeAgentCallback(final BaseAgent agent, final CallbackContext ctx) {
    RunStateUtils.initState(ctx == null ? null : ctx.invocationContext());
    return Maybe.empty();
  }

  @Override
  public Maybe<Content> afterAgentCallback(final BaseAgent agent, final CallbackContext ctx) {
    RunStateUtils.clearState(ctx == null ? null : ctx.invocationContext());
    return Maybe.empty();
  }
}
