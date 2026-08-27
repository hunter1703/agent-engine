package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;

public final class InitPlugin extends BasePlugin {

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
  public Maybe<Content> beforeAgentCallback(
      final BaseAgent agent, final CallbackContext callbackContext) {
    // Fires for every agent invocation in this session, including the entry agent's first call
    // and any agent reached later via transfer_to_agent — each gets its own SessionState (since a
    // session can be handled by multiple agents).
    final InvocationContext invocationContext = callbackContext.invocationContext();
    final ExtendedRunConfig runConfig =
        invocationContext.runConfig() instanceof ExtendedRunConfig extendedRunConfig
            ? extendedRunConfig
            : null;
    SessionUtils.getOrInitSessionState(
        invocationContext, knowledgeService, notebookRepository, notesRepository, runConfig);
    return Maybe.empty();
  }
}
