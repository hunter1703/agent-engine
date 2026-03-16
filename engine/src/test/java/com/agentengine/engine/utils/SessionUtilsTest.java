package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.Content;
import com.google.genai.types.Outcome;
import com.google.genai.types.Part;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class SessionUtilsTest {

  @Test
  void shouldRoundTripEventWithCodeExecutionResult() {
    final CodeExecutionResult codeResult = CodeExecutionResult.builder().outcome(Outcome.Known.OUTCOME_OK).output("42\n").build();
    final Event event = Event.builder()
        .content(Content.builder().role("model").parts(List.of(Part.builder().codeExecutionResult(codeResult).build())).build()).build();
    final Session session = Session.builder("test-session").appName("test-app").userId("user-1").state(new ConcurrentHashMap<>())
        .events(List.of(event)).lastUpdateTime(Instant.EPOCH).build();

    final Session restored = SessionUtils.toSession(SessionUtils.toSessionInfo(session));

    assertThat(restored.events()).hasSize(1);
    final Part part = restored.events().get(0).content().get().parts().get().get(0);
    assertThat(part.codeExecutionResult()).isPresent();
    assertThat(part.codeExecutionResult().get().outcome().get().knownEnum()).isEqualTo(Outcome.Known.OUTCOME_OK);
    assertThat(part.codeExecutionResult().get().output()).contains("42\n");
  }

  @Test
  void shouldRoundTripEventWithTextPart() {
    final Event event = Event.builder().content(Content.builder().role("model").parts(List.of(Part.fromText("hello"))).build()).build();
    final Session session = Session.builder("test-session").appName("test-app").userId("user-1").state(new ConcurrentHashMap<>())
        .events(List.of(event)).lastUpdateTime(Instant.EPOCH).build();

    final Session restored = SessionUtils.toSession(SessionUtils.toSessionInfo(session));

    assertThat(restored.events()).hasSize(1);
    final Part part = restored.events().get(0).content().get().parts().get().get(0);
    assertThat(part.text()).contains("hello");
  }
}
