package com.agentengine.engine.agents.processors;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParserTest {

  @Test
  void shouldKeepTransferFunctionStyleCallsAsText() {
    final Parser parser = new Parser("", true);
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.fromText(
                        "transfer_to_agent(\"story_phase_1_brief\")\n"
                            + "transfer_to_agent(\"story_phase_2_outline\")")))
            .build();

    final Content parsed = parse(parser, content);

    final List<Part> parts = parsed.parts().orElse(List.of());
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().functionCall()).isEmpty();
    assertThat(parts.getFirst().text().orElse(""))
        .contains("transfer_to_agent(\"story_phase_1_brief\")")
        .contains("transfer_to_agent(\"story_phase_2_outline\")");
  }

  @Test
  void shouldKeepTransferCamelCaseFunctionStyleCallsAsText() {
    final Parser parser = new Parser("", true);
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.fromText("transferToAgent(\"story_phase_3_draft\")")))
            .build();

    final Content parsed = parse(parser, content);

    final List<Part> parts = parsed.parts().orElse(List.of());
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().functionCall()).isEmpty();
    assertThat(parts.getFirst().text()).hasValue("transferToAgent(\"story_phase_3_draft\")");
  }

  @Test
  void shouldNotStripFunctionStyleTransferCallsFromTextPart() {
    final Parser parser = new Parser("", true);
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.fromText(
                        "Plan created.\ntransfer_to_agent(\"story_phase_1_brief\")\nProceeding.")))
            .build();

    final Content parsed = parse(parser, content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().text().orElse(""))
        .contains("transfer_to_agent(\"story_phase_1_brief\")");
    assertThat(parts.getFirst().functionCall()).isEmpty();
  }

  @Test
  void shouldNotParseNonTransferFunctionStyleCallsFromText() {
    final Parser parser = new Parser("", true);
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.fromText("run_cmd(\"echo hi\")")))
            .build();

    final Content parsed = parse(parser, content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    final List<Part> functionParts =
        parts.stream().filter(part -> part.functionCall().isPresent()).toList();
    assertThat(functionParts).isEmpty();
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().text()).hasValue("run_cmd(\"echo hi\")");
  }

  @Test
  void shouldDropEmptyPartsFromPreProcessedRequest() {
    final Parser parser = new Parser("", true);
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("   "), Part.fromText("keep")))
                        .build()))
            .build();

    final LlmRequest processed = parser.preProcess(request);

    final List<Part> parts = processed.contents().getFirst().parts().orElseThrow();
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().text()).hasValue("keep");
  }

  @Test
  void shouldStripToolPartsFromPreProcessedRequestWhenToolCallingDisabled() {
    final Parser parser = new Parser("", false);
    final Part functionCallPart =
        Part.builder()
            .functionCall(
                FunctionCall.builder().name("search").args(Map.of("query", "weather")).build())
            .build();
    final Part functionResponsePart =
        Part.builder()
            .functionResponse(
                FunctionResponse.builder()
                    .name("search")
                    .id("call-1")
                    .response(Map.of("result", "sunny"))
                    .build())
            .build();
    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText("keep"), functionCallPart, functionResponsePart))
                        .build()))
            .build();

    final LlmRequest processed = parser.preProcess(request);

    final List<Part> parts = processed.contents().getFirst().parts().orElseThrow();
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().text()).hasValue("keep");
  }

  private static Content parse(final Parser parser, final Content content) {
    return parser
        .postProcess(LlmResponse.builder().content(content).build())
        .content()
        .orElseThrow();
  }
}
