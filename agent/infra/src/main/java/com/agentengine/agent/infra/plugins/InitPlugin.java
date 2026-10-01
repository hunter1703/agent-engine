package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.SessionUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;

public final class InitPlugin extends BasePlugin {

  public InitPlugin() {
    super("init_plugin");
  }

  @Override
  public Maybe<Content> beforeAgentCallback(
      final BaseAgent agent, final CallbackContext callbackContext) {
    // Fires for every agent invocation in this session, including the entry agent's first call
    // and any agent reached later via transfer_to_agent — each gets its own SessionState (since a
    // session can be handled by multiple agents).
    SessionUtils.getOrInitSessionState(callbackContext.invocationContext());
    return Maybe.empty();
  }
}
