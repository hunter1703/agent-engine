package com.agentengine.agent.core.services;

import com.agentengine.agent.api.services.SessionJournalService;
import com.agentengine.agent.core.session.SessionActorJournal;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@Unremovable
public class SessionJournalServiceImpl implements SessionJournalService {

  private final SessionActorJournal sessionActorJournal;

  @Inject
  public SessionJournalServiceImpl(final SessionActorJournal sessionActorJournal) {
    this.sessionActorJournal = sessionActorJournal;
  }

  @Override
  public List<String> getCommittedTurnIds(final String sessionId) {
    return sessionActorJournal.getCommittedTurnIds(sessionId);
  }
}
