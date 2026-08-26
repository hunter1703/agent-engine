package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.api.model.ResourceGrants;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
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
  private final NotebookRepository notebookRepository;
  private final NotesRepository notesRepository;

  public InitPlugin(
      KnowledgeService knowledgeService,
      NotebookRepository notebookRepository,
      NotesRepository notesRepository) {
    super("init_plugin");
    this.knowledgeService = knowledgeService;
    this.notebookRepository = notebookRepository;
    this.notesRepository = notesRepository;
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
        SessionUtils.getOrInitSessionState(
            invocationContext, knowledgeService, notebookRepository, notesRepository);
    LOG.info(
        "[DIAG] InitPlugin after getOrInitSessionState agentStatesMap={} keys={}",
        System.identityHashCode(invocationContext.agentStates()),
        invocationContext.agentStates().keySet());
    if (invocationContext.runConfig() instanceof ExtendedRunConfig extended) {
      final ResourceGrants grants = extended.grants();
      sessionState.addKnowledgeIdReminders(grants.knowledgeIds());
      sessionState.addKnowledgeSourceReminders(grants.knowledgeSources());
      sessionState.addNotebookReminders(grants.notebookGrants());
    }
    return Maybe.empty();
  }
}
