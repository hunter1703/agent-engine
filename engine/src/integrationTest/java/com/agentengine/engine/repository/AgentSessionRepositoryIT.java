package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.testing.MongoRedisTestResource;
import com.agentengine.util.mongo.MongoClientFactory;
import com.google.adk.events.Event;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(MongoRedisTestResource.class)
class AgentSessionRepositoryIT {

  @Inject AgentSessionRepository agentSessionRepository;

  @Inject MongoClientFactory mongoClientFactory;

  @BeforeEach
  void shouldResetDatabaseWhenTestStarts() {
    try (MongoClient client = mongoClientFactory.getClient()) {
      client.getDatabase("AGENT_ENGINE").drop();
    }
  }

  @Test
  void shouldCreateAndFetchSessionWhenSessionExists() {
    final Session created =
        agentSessionRepository
            .createSession(
                "agent-1",
                "user-1",
                new ConcurrentHashMap<>(Map.of("status", "active")),
                "session-1")
            .blockingGet();

    final Session fetched =
        agentSessionRepository
            .getSession("agent-1", "user-1", "session-1", Optional.empty())
            .blockingGet();

    assertThat(created.id()).isEqualTo("session-1");
    assertThat(fetched).isNotNull();
    assertThat(fetched.state()).containsEntry("status", "active");
  }

  @Test
  void shouldAppendAndPersistEventWhenAppendEventCalled() {
    final Session created =
        agentSessionRepository
            .createSession("agent-1", "user-1", new ConcurrentHashMap<>(), "session-1")
            .blockingGet();

    final Event event =
        Event.builder()
            .id("event-1")
            .invocationId("inv-1")
            .author("agent")
            .timestamp(Instant.now().toEpochMilli())
            .content(
                Content.builder().role("model").parts(List.of(Part.fromText("hello"))).build())
            .build();

    agentSessionRepository.appendEvent(created, event).blockingGet();

    final var response = agentSessionRepository.listEvents("agent-1", "user-1", "session-1").blockingGet();
    assertThat(response.events()).hasSize(1);
    assertThat(response.events().getFirst().id()).isEqualTo("event-1");

    final Session fetchedWithConfig =
        agentSessionRepository
            .getSession(
                "agent-1",
                "user-1",
                "session-1",
                Optional.of(GetSessionConfig.builder().numRecentEvents(1).build()))
            .blockingGet();
    assertThat(fetchedWithConfig.events()).hasSize(1);
  }

  @Test
  void shouldDeleteSessionWhenDeleteSessionCalled() {
    agentSessionRepository
        .createSession("agent-1", "user-1", new ConcurrentHashMap<>(), "session-a")
        .blockingGet();

    agentSessionRepository.deleteSession("agent-1", "user-1", "session-a").blockingAwait();
    final Session deleted =
        agentSessionRepository
            .getSession("agent-1", "user-1", "session-a", Optional.empty())
            .blockingGet();

    assertThat(deleted).isNull();
  }
}
