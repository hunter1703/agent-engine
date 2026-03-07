package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.response.GuardrailResponseProcessor;
import com.agentengine.engine.api.beans.config.GuardrailsConfig;
import com.agentengine.engine.api.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.engine.api.beans.config.RelevanceMode;
import com.agentengine.engine.guardrails.GuardrailRuntime;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class GuardrailResponseProcessorTest {

  @Test
  void stripsOffTopicTextAndAddsCorrectionOnFirstViolation() {
    Session session =
        Session.builder("session-1")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    session
        .events()
        .add(
            com.google.adk.events.Event.builder()
                .id("u1")
                .author("user")
                .content(Content.builder().role("user").parts(Part.fromText("Explain Java streams")).build())
                .build());
    InvocationContext context = InvocationContext.builder().session(session).build();

    final OutputRelevanceGuardrailRule relevanceRule = new OutputRelevanceGuardrailRule();
    relevanceRule.setMode(RelevanceMode.STEER_THEN_BLOCK);
    relevanceRule.setMaxSteeringRetries(1);
    relevanceRule.setRelevanceThreshold(0.4);
    GuardrailsConfig config = new GuardrailsConfig();
    config.setRules(List.of(relevanceRule));
    GuardrailRuntime.attachConfig(session.state(), config);

    LlmResponse response =
        LlmResponse.builder()
            .content(Content.builder().role("model").parts(List.of(Part.fromText("Football scores tonight"))).build())
            .partial(false)
            .turnComplete(true)
            .build();

    LlmResponse updated =
        GuardrailResponseProcessor.INSTANCE
            .processResponse(context, response)
            .blockingGet()
            .updatedResponse();

    assertThat(updated.turnComplete()).contains(false);
    assertThat(updated.content()).isEmpty();
    assertThat(RunStateUtils.getState(context).violations())
        .extracting(v -> v.getCode())
        .contains("relevance_steer");
  }

  @Test
  void blocksAfterSteeringRetriesAreExceeded() {
    Session session =
        Session.builder("session-2")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    session
        .events()
        .add(
            com.google.adk.events.Event.builder()
                .id("u1")
                .author("user")
                .content(Content.builder().role("user").parts(Part.fromText("Describe Kubernetes pods")).build())
                .build());
    InvocationContext context = InvocationContext.builder().session(session).build();

    final OutputRelevanceGuardrailRule relevanceRule = new OutputRelevanceGuardrailRule();
    relevanceRule.setMode(RelevanceMode.STEER_THEN_BLOCK);
    relevanceRule.setMaxSteeringRetries(1);
    relevanceRule.setRelevanceThreshold(0.4);
    GuardrailsConfig config = new GuardrailsConfig();
    config.setRules(List.of(relevanceRule));
    GuardrailRuntime.attachConfig(session.state(), config);

    LlmResponse response =
        LlmResponse.builder()
            .content(Content.builder().role("model").parts(List.of(Part.fromText("Basketball finals recap"))).build())
            .partial(false)
            .turnComplete(true)
            .build();

    GuardrailResponseProcessor.INSTANCE.processResponse(context, response).blockingGet();
    LlmResponse blocked =
        GuardrailResponseProcessor.INSTANCE
            .processResponse(context, response)
            .blockingGet()
            .updatedResponse();

    assertThat(blocked.turnComplete()).contains(true);
    assertThat(blocked.content()).isPresent();
    assertThat(blocked.content().orElseThrow().text()).contains("blocked");
  }
}
