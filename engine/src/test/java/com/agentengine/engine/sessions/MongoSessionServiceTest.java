package com.agentengine.engine.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

class MongoSessionServiceTest {

  @Test
  void roundTripInfoPreservesSessionFields() {
    final ConcurrentMap<String, Object> state = new ConcurrentHashMap<>();
    state.put("region", "us-west");
    final Event event = Event.builder().id("event-1").author("user").content(Content.fromParts(Part.fromText("hello")))
        .timestamp(1234L).build();
    final Session session = Session.builder("session-1").appName("app").userId("user").state(state)
        .events(List.of(event)).lastUpdateTime(Instant.ofEpochSecond(42)).build();

    final SessionInfo info = new SessionInfo(session);
    final Session restored = info.toSession();

    assertThat(restored.id()).isEqualTo("session-1");
    assertThat(restored.appName()).isEqualTo("app");
    assertThat(restored.userId()).isEqualTo("user");
    assertThat(restored.state()).containsEntry("region", "us-west");
    assertThat(restored.events()).hasSize(1);
    assertThat(restored.events().get(0).author()).isEqualTo("user");
    assertThat(restored.lastUpdateTime().getEpochSecond()).isEqualTo(42);
  }
}
