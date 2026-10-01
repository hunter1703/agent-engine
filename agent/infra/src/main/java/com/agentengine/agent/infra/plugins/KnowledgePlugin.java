package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;

/** Tells an agent, once per run, which knowledge items it can reach. */
public final class KnowledgePlugin extends BasePlugin {

  private final KnowledgeService knowledgeService;

  public KnowledgePlugin(final KnowledgeService knowledgeService) {
    super("knowledge_plugin");
    this.knowledgeService = knowledgeService;
  }

  @Override
  public Maybe<Content> beforeAgentCallback(
      final BaseAgent agent, final CallbackContext callbackContext) {
    final InvocationContext invocationContext = callbackContext.invocationContext();
    if (SessionUtils.isNewRun(invocationContext)) {
      SessionUtils.getSessionState(invocationContext).syncKnowledgeReminders(knowledgeService);
    }
    return Maybe.empty();
  }
}
