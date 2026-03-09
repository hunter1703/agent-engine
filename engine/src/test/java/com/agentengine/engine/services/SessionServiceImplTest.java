package com.agentengine.engine.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.update.Operation;
import com.agentengine.engine.api.update.Update;
import com.agentengine.engine.events.SessionDeletedEvent;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.engine.utils.EncryptionService;
import jakarta.enterprise.event.Event;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

  @Mock private AgentSessionRepository sessionRepository;

  @Mock private EncryptionService encryptionService;

  @Mock private Event<SessionDeletedEvent> sessionDeletedEvent;

  @Test
  void shouldDelegateGetSessionWhenGetSessionCalled() {
    final SessionServiceImpl service =
        new SessionServiceImpl(sessionRepository, encryptionService, sessionDeletedEvent);
    final AgentSession session = new AgentSession("session-1", "agent-1", "title");
    when(sessionRepository.findById("session-1")).thenReturn(Optional.of(session));

    final Optional<AgentSession> result = service.getSession("session-1");

    assertThat(result).contains(session);
    verify(sessionRepository).findById("session-1");
  }

  @Test
  void shouldEncryptTitleAndUpdateSessionWhenUpdateTitleCalled() {
    final SessionServiceImpl service =
        new SessionServiceImpl(sessionRepository, encryptionService, sessionDeletedEvent);
    when(encryptionService.encrypt("My Title")).thenReturn("enc-title");

    service.updateTitle("session-1", "My Title");

    final ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(sessionRepository).update(eq("session-1"), updateCaptor.capture());

    final Update update = updateCaptor.getValue();
    assertThat(update.operations()).containsExactly(Operation.set("title", "enc-title"));
    verify(encryptionService).encrypt("My Title");
  }

  @Test
  void shouldDeleteSessionAndPublishEventWhenDeleteSessionCalled() {
    final SessionServiceImpl service =
        new SessionServiceImpl(sessionRepository, encryptionService, sessionDeletedEvent);

    service.deleteSession("session-1");

    verify(sessionRepository).deleteById("session-1");
    final ArgumentCaptor<SessionDeletedEvent> eventCaptor =
        ArgumentCaptor.forClass(SessionDeletedEvent.class);
    verify(sessionDeletedEvent).fire(eventCaptor.capture());
    assertThat(eventCaptor.getValue().sessionId()).isEqualTo("session-1");
  }
}
