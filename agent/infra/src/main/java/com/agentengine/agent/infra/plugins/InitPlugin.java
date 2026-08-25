package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.google.adk.agents.InvocationContext;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InitPlugin extends BasePlugin {

  private static final Logger LOG = LoggerFactory.getLogger(InitPlugin.class);

  private final KnowledgeService knowledgeService;

  public InitPlugin(KnowledgeService knowledgeService) {
    super("init_plugin");
    this.knowledgeService = knowledgeService;
  }

  @Override
  public Maybe<Content> onUserMessageCallback(
      InvocationContext invocationContext, Content userMessage) {
    LOG.info(
        "[DIAG] InitPlugin.onUserMessageCallback ctx={} agent={} agentStatesMap={} session={}",
        System.identityHashCode(invocationContext),
        invocationContext.agent() == null ? "null" : invocationContext.agent().name(),
        System.identityHashCode(invocationContext.agentStates()),
        invocationContext.session().id());
    final SessionState sessionState =
        SessionUtils.getOrInitSessionState(invocationContext, knowledgeService);
    LOG.info(
        "[DIAG] InitPlugin after getOrInitSessionState agentStatesMap={} keys={}",
        System.identityHashCode(invocationContext.agentStates()),
        invocationContext.agentStates().keySet());
    if (invocationContext.runConfig() instanceof ExtendedRunConfig extended) {
      sessionState.addKnowledgeIdReminders(extended.grants().knowledgeIds());
      sessionState.addKnowledgeSourceReminders(extended.grants().knowledgeSources());
    }
    return Maybe.empty();
  }
}
