package com.agentengine.engine.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.Test;

class RelevanceEvaluatorTest {

  @Test
  void combinesRelevanceAndIrrelevanceScoresFromModel() {
    final BaseLlm model =
        new PromptAwareModel(
            PromptAwareModel.ResponseConfig.builder()
                .relevanceResponse("{\"score\": 80}")
                .irrelevanceResponse("{\"score\": 20}")
                .build());

    final double score =
        RelevanceEvaluator.scoreWithModel(
            model, "Explain Java streams", "Java streams map and filter collections.");

    assertThat(score).isEqualTo(0.8D);
  }

  @Test
  void parsesNonJsonNumericScores() {
    final BaseLlm model =
        new PromptAwareModel(
            PromptAwareModel.ResponseConfig.builder()
                .relevanceResponse("score: 70")
                .irrelevanceResponse("irrelevance=40")
                .build());

    final double score =
        RelevanceEvaluator.scoreWithModel(
            model, "Kubernetes pods", "Pods are basic deployable units.");

    assertThat(score).isEqualTo(0.65D);
  }

  private static final class PromptAwareModel extends BaseLlm {
    private final ResponseConfig config;

    private PromptAwareModel(final ResponseConfig config) {
      super("eval-model");
      this.config = config;
    }

    @Override
    public Flowable<LlmResponse> generateContent(final LlmRequest llmRequest, final boolean stream) {
      final String prompt = llmRequest.contents().isEmpty() ? "" : llmRequest.contents().getFirst().text();
      final String text =
          prompt.toLowerCase().contains("irrelevant")
              ? config.irrelevanceResponse()
              : config.relevanceResponse();
      final Content content = Content.builder().role("model").parts(Part.fromText(text)).build();
      return Flowable.just(LlmResponse.builder().content(content).build());
    }

    @Override
    public BaseLlmConnection connect(final LlmRequest llmRequest) {
      return null;
    }

    private record ResponseConfig(String relevanceResponse, String irrelevanceResponse) {
      private static Builder builder() {
        return new Builder();
      }

      private static final class Builder {
        private String relevanceResponse;
        private String irrelevanceResponse;

        private Builder relevanceResponse(final String value) {
          this.relevanceResponse = value;
          return this;
        }

        private Builder irrelevanceResponse(final String value) {
          this.irrelevanceResponse = value;
          return this;
        }

        private ResponseConfig build() {
          return new ResponseConfig(relevanceResponse, irrelevanceResponse);
        }
      }
    }
  }
}
