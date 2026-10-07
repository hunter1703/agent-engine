package com.agentengine.agent.infra.reminders;

import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.knowledge.api.services.KnowledgeService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KnowledgeReminderSyncer implements ReminderSyncer {
  private final KnowledgeService knowledgeService;

  @Inject
  public KnowledgeReminderSyncer(final KnowledgeService knowledgeService) {
    this.knowledgeService = knowledgeService;
  }

  @Override
  public void sync(final SessionState sessionState) {
    sessionState.syncKnowledgeReminders(knowledgeService);
  }
}
