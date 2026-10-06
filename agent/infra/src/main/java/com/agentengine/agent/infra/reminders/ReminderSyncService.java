package com.agentengine.agent.infra.reminders;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.knowledge.api.services.KnowledgeService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ReminderSyncService {

  private final NotebookService notebookService;
  private final KnowledgeService knowledgeService;

  @Inject
  public ReminderSyncService(
      final NotebookService notebookService, final KnowledgeService knowledgeService) {
    this.notebookService = notebookService;
    this.knowledgeService = knowledgeService;
  }

  public void syncAll(final SessionState sessionState) {
    sessionState.syncNotebookReminder(notebookService);
    sessionState.syncKnowledgeReminders(knowledgeService);
  }
}
