package com.agentengine.engine.agents.processors.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TurnCompletionResponseProcessorTest {

  @Test
  void shouldMarkNonPartialToolResponsesCompleteWhenMissingTurnCompletion() {
    final LlmResponse response =
        LlmResponse.builder()
            .content(
                Content.builder()
                    .role("model")
                    .parts(
                        List.of(
                            Part.builder()
                                .functionCall(
                                    FunctionCall.builder()
                                        .id("call-1")
                                        .name("search")
                                        .args(Map.of("query", "weather"))
                                        .build())
                                .build()))
                    .build())
            .build();

    final LlmResponse updated =
        TurnCompletionResponseProcessor.INSTANCE
            .processResponse(null, response)
            .blockingGet()
            .updatedResponse();

    assertThat(updated.turnComplete()).contains(true);
  }
}
