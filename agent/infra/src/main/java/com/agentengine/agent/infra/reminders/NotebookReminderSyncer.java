package com.agentengine.agent.infra.reminders;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.utils.SessionState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NotebookReminderSyncer implements ReminderSyncer {
    private final NotebookService notebookService;

    @Inject
    public NotebookReminderSyncer(final NotebookService notebookService) {
        this.notebookService = notebookService;
    }

    @Override
    public void sync(final SessionState sessionState) {
        sessionState.syncNotebookReminder(notebookService);
    }
}
