package com.agentengine.engine.api.beans.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class SessionInfoTest {

  @Test
  void shouldRoundTripSessionWhenConvertingFromAndToSession() {
    final Event event = Event.builder().id("event-1").invocationId("inv-1").author("user").timestamp(Instant.now().toEpochMilli())
        .content(Content.builder().role("user").parts(List.of(Part.fromText("hello"))).build()).build();
    final Session sourceSession = Session.builder("session-1").appName("agent-a").userId("user-a")
        .state(new ConcurrentHashMap<>(Map.of("key", "value"))).events(List.of(event)).lastUpdateTime(Instant.now()).build();

    final SessionInfo sessionInfo = SessionUtils.fromSession(sourceSession);
    final Session restored = SessionUtils.toSession(sessionInfo);

    assertThat(restored.id()).isEqualTo(sourceSession.id());
    assertThat(restored.appName()).isEqualTo(sourceSession.appName());
    assertThat(restored.userId()).isEqualTo(sourceSession.userId());
    assertThat(restored.state()).containsEntry("key", "value");
    assertThat(restored.events()).hasSize(1);
    assertThat(restored.events().getFirst().id()).isEqualTo("event-1");
  }

  @Test
  void shouldFallbackToEmptyEventsWhenEventsJsonIsBlankOrNullLiteral() {
    final SessionInfo blank = new SessionInfo();
    blank.setEventsJson("   ");
    assertThat(blank.getEvents()).isEmpty();

    final SessionInfo nullLiteral = new SessionInfo();
    nullLiteral.setEventsJson("null");
    assertThat(nullLiteral.getEvents()).isEmpty();
  }
}
