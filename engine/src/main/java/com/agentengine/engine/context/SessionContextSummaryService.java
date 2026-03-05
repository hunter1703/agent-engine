package com.agentengine.engine.context;

import com.agentengine.engine.api.beans.session.SessionContextSummary;
import com.agentengine.engine.events.SessionDeletedEvent;
import com.agentengine.engine.repository.SessionContextSummaryRepository;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SessionContextSummaryService {
  private static final Logger LOG = LoggerFactory.getLogger(SessionContextSummaryService.class);
  private final SessionContextSummaryRepository repository;

  public SessionContextSummaryService(final SessionContextSummaryRepository repository) {
    this.repository = repository;
  }

  public Optional<SessionContextSummary> get(final String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    try {
      return repository.findById(sessionId);
    } catch (Exception ex) {
      LOG.warn("Failed to load context summary for session_id={}", sessionId, ex);
      return Optional.empty();
    }
  }

  public void upsert(
      final String sessionId,
      final String agentId,
      final String summaryText,
      final int summarizedUntilContentCount,
      final int sourceTokens,
      final String modelId) {
    if (sessionId == null || sessionId.isBlank() || summaryText == null || summaryText.isBlank()) {
      return;
    }
    final long now = System.currentTimeMillis();
    final SessionContextSummary entity = get(sessionId).orElseGet(SessionContextSummary::new);
    if (entity.getCreatedTime() == null) {
      entity.setCreatedTime(now);
    }
    entity.setId(sessionId);
    entity.setSessionId(sessionId);
    entity.setAgentId(agentId);
    entity.setSummaryText(summaryText);
    entity.setSummarizedUntilContentCount(Math.max(0, summarizedUntilContentCount));
    entity.setSourceTokens(sourceTokens);
    entity.setModelId(modelId);
    entity.setUpdatedTime(now);
    try {
      repository.save(entity);
    } catch (Exception ex) {
      LOG.warn("Failed to persist context summary for session_id={}", sessionId, ex);
    }
  }

  public void delete(final String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return;
    }
    try {
      repository.deleteById(sessionId);
    } catch (Exception ex) {
      LOG.warn("Failed to delete context summary for session_id={}", sessionId, ex);
    }
  }

  void onSessionDeleted(@Observes final SessionDeletedEvent event) {
    if (event != null) {
      delete(event.sessionId());
    }
  }
}
