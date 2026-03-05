package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.session.SessionContextSummary;
import com.agentengine.engine.events.SessionDeletedEvent;
import com.agentengine.engine.repository.SessionContextSummaryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionContextSummaryServiceTest {

  @Test
  void upsertCreatesOrUpdatesSummaryDocument() {
    final SessionContextSummaryRepository repository = mock(SessionContextSummaryRepository.class);
    final SessionContextSummaryService service = new SessionContextSummaryService(repository);
    when(repository.findById("s1")).thenReturn(Optional.empty());

    service.upsert("s1", "agent-a", "summary text", 7, "hash-1", 42, "model-1");

    final var captor = forClass(SessionContextSummary.class);
    verify(repository).save(captor.capture());
    final SessionContextSummary persisted = captor.getValue();
    assertThat(persisted.getId()).isEqualTo("s1");
    assertThat(persisted.getSessionId()).isEqualTo("s1");
    assertThat(persisted.getAgentId()).isEqualTo("agent-a");
    assertThat(persisted.getSummaryText()).isEqualTo("summary text");
    assertThat(persisted.getSummarizedUntilContentCount()).isEqualTo(7);
    assertThat(persisted.getSourceHash()).isEqualTo("hash-1");
    assertThat(persisted.getSourceTokens()).isEqualTo(42);
    assertThat(persisted.getModelId()).isEqualTo("model-1");
  }

  @Test
  void deletesSummaryWhenSessionDeletedEventArrives() {
    final SessionContextSummaryRepository repository = mock(SessionContextSummaryRepository.class);
    final SessionContextSummaryService service = new SessionContextSummaryService(repository);

    service.onSessionDeleted(new SessionDeletedEvent("s2"));

    verify(repository).deleteById("s2");
  }
}
