package com.agentengine.engine.agents.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.response.OutputEvaluationResponseProcessor;
import com.agentengine.engine.api.beans.config.EvaluatorStopPolicy;
import com.agentengine.engine.api.beans.config.OutputEvaluationConfig;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class OutputEvaluationResponseProcessorTest {

  @Test
  void requestsRetryWhenScoreBelowThreshold() {
    final Session session =
        Session.builder("session-1")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    session.events().add(userEvent("Explain Java streams"));
    final InvocationContext context = InvocationContext.builder().session(session).build();

    final OutputEvaluationConfig config = new OutputEvaluationConfig();
    config.setEnabled(true);
    config.setMinScore(0.6);
    config.setMaxRetries(2);
    final OutputEvaluationResponseProcessor processor =
        new OutputEvaluationResponseProcessor(config, (_anchor, _output) -> 0.1);

    final LlmResponse response =
        LlmResponse.builder()
            .content(Content.builder().role("model").parts(List.of(Part.fromText("Football rankings"))).build())
            .partial(false)
            .turnComplete(true)
            .build();

    final LlmResponse updated =
        processor
            .processResponse(context, response)
            .blockingGet()
            .updatedResponse();

    assertThat(updated.turnComplete()).contains(false);
    assertThat(RunStateUtils.getState(context).violations())
        .extracting(v -> v.getCode())
        .contains("output_evaluation_retry");
  }

  @Test
  void blocksAfterRetryLimitWhenStopPolicyIsBlock() {
    final Session session =
        Session.builder("session-2")
            .appName("agent")
            .userId("default")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    session.events().add(userEvent("Explain Kubernetes pods"));
    final InvocationContext context = InvocationContext.builder().session(session).build();

    final OutputEvaluationConfig config = new OutputEvaluationConfig();
    config.setEnabled(true);
    config.setMinScore(0.6);
    config.setMaxRetries(1);
    config.setStopPolicy(EvaluatorStopPolicy.RETRY_THEN_BLOCK);
    final OutputEvaluationResponseProcessor processor =
        new OutputEvaluationResponseProcessor(config, (_anchor, _output) -> 0.1);

    final LlmResponse response =
        LlmResponse.builder()
            .content(Content.builder().role("model").parts(List.of(Part.fromText("NBA standings"))).build())
            .partial(false)
            .turnComplete(true)
            .build();

    processor.processResponse(context, response).blockingGet();
    final LlmResponse blocked =
        processor
            .processResponse(context, response)
            .blockingGet()
            .updatedResponse();

    assertThat(blocked.turnComplete()).contains(true);
    assertThat(blocked.content()).isPresent();
    assertThat(blocked.content().orElseThrow().text()).contains("blocked by evaluator");
  }

  private static Event userEvent(final String text) {
    return Event.builder()
        .id("u-" + text.hashCode())
        .author("user")
        .content(Content.builder().role("user").parts(Part.fromText(text)).build())
        .build();
  }
}
