package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.response.TurnCompletionResponseProcessor;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class TurnCompletionResponseProcessorTest {

  @Test
  void doesNotOverrideExplicitTurnCompleteFalse() {
    final Session session =
        Session.builder("s1")
            .appName("agent")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).build();

    final LlmResponse response =
        LlmResponse.builder()
            .content(Content.builder().role("model").parts(Part.fromText("text")).build())
            .turnComplete(false)
            .partial(false)
            .build();

    final LlmResponse updated =
        TurnCompletionResponseProcessor.INSTANCE
            .processResponse(context, response)
            .blockingGet()
            .updatedResponse();

    assertThat(updated.turnComplete()).contains(false);
  }

  @Test
  void setsTurnCompleteWhenUnsetAndHasVisibleText() {
    final Session session =
        Session.builder("s2")
            .appName("agent")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).build();

    final LlmResponse response =
        LlmResponse.builder()
            .content(Content.builder().role("model").parts(Part.fromText("text")).build())
            .partial(false)
            .build();

    final LlmResponse updated =
        TurnCompletionResponseProcessor.INSTANCE
            .processResponse(context, response)
            .blockingGet()
            .updatedResponse();

    assertThat(updated.turnComplete()).contains(true);
  }
}
