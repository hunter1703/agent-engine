package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NormalizedLangChain4jTest {

  @Test
  void shouldNormalizeStreamingTextWithoutClaimingFinalTurnCompletion() {
    final List<LlmResponse> responses =
        NormalizedLangChain4j
            .normalizeStreamingResponses(Flowable.just(textResponse("Hel"), textResponse("lo")))
            .toList()
            .blockingGet();

    assertThat(responses).hasSize(3);
    assertThat(responses.getFirst().partial()).contains(true);
    assertThat(responses.getFirst().turnComplete()).contains(false);

    final LlmResponse finalResponse = responses.getLast();
    assertThat(finalResponse.partial()).contains(false);
    assertThat(finalResponse.turnComplete()).isEmpty();
    assertThat(finalResponse.content()).isPresent();
    assertThat(finalResponse.content().orElseThrow().role()).contains("model");
    assertThat(finalResponse.content().orElseThrow().text()).isEqualTo("Hello");
  }

  @Test
  void shouldLeaveToolCallSanitizationToResponseProcessors() {
    final List<LlmResponse> responses =
        NormalizedLangChain4j
            .normalizeStreamingResponses(Flowable.just(toolCallResponseWithoutId()))
            .toList()
            .blockingGet();

    assertThat(responses).hasSize(1);

    final LlmResponse response = responses.getFirst();
    assertThat(response.partial()).contains(false);
    assertThat(response.turnComplete()).isEmpty();
    assertThat(response.content()).isPresent();
    assertThat(response.content().orElseThrow().role()).contains("model");
    assertThat(
            response
                .content()
                .orElseThrow()
                .parts()
                .orElseThrow()
                .getFirst()
                .functionCall()
                .orElseThrow()
                .id())
        .isEmpty();
  }

  private static LlmResponse textResponse(final String text) {
    return LlmResponse.builder()
        .content(Content.builder().role("user").parts(List.of(Part.fromText(text))).build())
        .build();
  }

  private static LlmResponse toolCallResponseWithoutId() {
    return LlmResponse.builder()
        .content(
            Content.builder()
                .role("user")
                .parts(
                    List.of(
                        Part.builder()
                            .functionCall(
                                FunctionCall.builder()
                                    .name("search")
                                    .args(Map.of("query", "weather"))
                                    .build())
                            .build()))
                .build())
        .build();
  }
}
