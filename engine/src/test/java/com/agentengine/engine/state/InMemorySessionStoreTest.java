package com.agentengine.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Session;
import org.junit.jupiter.api.Test;

class InMemorySessionStoreTest {

  @Test
  void createOrUpdateAssignsIdWhenMissing() {
    final InMemorySessionStore sessionStore = new InMemorySessionStore();
    final Session session = new Session();
    session.setAgentId("agent");

    final Session stored = sessionStore.createOrUpdate(session);

    assertThat(stored.getId()).isNotBlank();
    assertThat(sessionStore.findById(stored.getId())).isSameAs(stored);
  }

  @Test
  void createOrUpdateReplacesExistingSession() {
    final InMemorySessionStore sessionStore = new InMemorySessionStore();
    final Session original = new Session();
    original.setId("session-1");
    sessionStore.createOrUpdate(original);

    final Session updated = new Session();
    updated.setId("session-1");
    updated.setRunId("run");
    sessionStore.createOrUpdate(updated);

    final Session stored = sessionStore.findById("session-1");
    assertThat(stored.getRunId()).isEqualTo("run");
  }
}
