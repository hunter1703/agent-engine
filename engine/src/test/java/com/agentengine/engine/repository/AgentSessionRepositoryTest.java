package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.update.Update;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.mongodb.client.MongoCollection;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentSessionRepositoryTest {

  private AgentSessionRepository repository;
  private MongoClientSupport mongoClientSupport;
  private MongoCollection<AgentSession> collection;

  @BeforeEach
  void setUp() {
    mongoClientSupport = mock(MongoClientSupport.class);
    collection = mock(MongoCollection.class);
    repository = spy(new AgentSessionRepository(mongoClientSupport) {
      @Override
      protected MongoCollection<AgentSession> getCollection() {
        return collection;
      }
    });
  }

  @Test
  void appendEventPersistsNonPartialEvent() {
    final Session session = createSession("session-id");
    final Event event = Event.builder()
        .id("event-id")
        .partial(false)
        .invocationId("inv-id")
        .author("author")
        .build();

    repository.appendEvent(session, event).blockingGet();

    assertThat(session.events()).contains(event);
    verify(repository).update(eq("session-id"), any(Update.class));
  }

  @Test
  void appendEventSkipsPartialEventWithoutFunctionCalls() {
    final Session session = createSession("session-id");
    final Event event = Event.builder()
        .id("event-id")
        .partial(true)
        .invocationId("inv-id")
        .author("author")
        .build();

    repository.appendEvent(session, event).blockingGet();

    assertThat(session.events()).doesNotContain(event);
    verify(repository, never()).update(eq("session-id"), any(Update.class));
  }

  @Test
  void appendEventSkipsPartialEventEvenWithFunctionCalls() {
    final Session session = createSession("session-id");
    final com.google.genai.types.Content content = com.google.genai.types.Content.builder()
        .role("model")
        .parts(com.google.genai.types.Part.builder()
            .functionCall(com.google.genai.types.FunctionCall.builder()
                .name("test_tool")
                .args(new java.util.HashMap<>())
                .id("call-id")
                .build())
            .build())
        .build();

    final Event event = Event.builder()
        .id("event-id")
        .partial(true)
        .invocationId("inv-id")
        .author("author")
        .content(content)
        .build();

    repository.appendEvent(session, event).blockingGet();

    assertThat(session.events()).doesNotContain(event);
    verify(repository, never()).update(eq("session-id"), any(Update.class));
  }

  private Session createSession(String id) {
    return Session.builder(id)
        .appName("agent-id")
        .userId("user-id")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .lastUpdateTime(Instant.now())
        .build();
  }
}
