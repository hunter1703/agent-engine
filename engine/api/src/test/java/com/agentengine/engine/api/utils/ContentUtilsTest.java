package com.agentengine.engine.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentUtilsTest {

  @Test
  void shouldExtractLatestUserTextFromContents() {
    final List<Content> contents =
        List.of(
            Content.builder().role("user").parts(List.of(Part.fromText("first"))).build(),
            Content.builder().role("model").parts(List.of(Part.fromText("ignore"))).build(),
            Content.builder().role("user").parts(List.of(Part.fromText("last"))).build());

    assertThat(ContentUtils.extractLatestUserText(contents)).isEqualTo("last");
  }

  @Test
  void shouldFindLatestUserContentIndex() {
    final List<Content> contents =
        List.of(
            Content.builder().role("model").parts(List.of(Part.fromText("m"))).build(),
            Content.builder().role("user").parts(List.of(Part.fromText("u1"))).build(),
            Content.builder().parts(List.of(Part.fromText("implicit-user"))).build());

    assertThat(ContentUtils.findLatestUserContentIndex(contents)).isEqualTo(2);
  }

  @Test
  void shouldExtractLatestUserTextFromRequest() {
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder().role("model").parts(List.of(Part.fromText("m"))).build(),
                    Content.builder().role("user").parts(List.of(Part.fromText("answer"))).build()))
            .build();

    assertThat(ContentUtils.extractLatestUserText(request)).isEqualTo("answer");
  }

  @Test
  void shouldTreatBlankTextOnlyContentAsEmpty() {
    final Content content =
        Content.builder().role("user").parts(List.of(Part.fromText("   "))).build();

    assertThat(ContentUtils.isEmptyPart(content)).isTrue();
  }

  @Test
  void shouldTreatFunctionCallPartAsNonEmpty() {
    final Part part =
        Part.builder()
            .functionCall(FunctionCall.builder().name("search").args(Map.of("q", "weather")).build())
            .build();

    assertThat(ContentUtils.isEmptyPart(part)).isFalse();
    assertThat(
            ContentUtils.isEmptyPart(
                Content.builder().role("model").parts(List.of(part)).build()))
        .isFalse();
  }
}
