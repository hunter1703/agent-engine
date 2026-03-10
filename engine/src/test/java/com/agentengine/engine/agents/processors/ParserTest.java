package com.agentengine.engine.agents.processors;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParserTest {

  @Test
  void shouldParseTransferFunctionStyleCallsFromText() {
    final Parser parser =
        Parser.builder().toolCallingEnabled(true).parseToolCallsFromText(true).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.fromText(
                        "transfer_to_agent(\"story_phase_1_brief\")\n"
                            + "transfer_to_agent(\"story_phase_2_outline\")")))
            .build();

    final Content parsed = parser.parse(content);

    final List<Part> parts = parsed.parts().orElse(List.of());
    final List<Part> functionParts =
        parts.stream().filter(part -> part.functionCall().isPresent()).toList();
    assertThat(functionParts).hasSize(2);
    assertThat(functionParts.get(0).functionCall().orElseThrow().name())
        .contains("transfer_to_agent");
    assertThat(functionParts.get(0).functionCall().orElseThrow().args().orElseThrow())
        .containsEntry("agent_name", "story_phase_1_brief");
    assertThat(functionParts.get(1).functionCall().orElseThrow().args().orElseThrow())
        .containsEntry("agent_name", "story_phase_2_outline");
  }

  @Test
  void shouldParseTransferCamelCaseFunctionStyleCallsFromText() {
    final Parser parser =
        Parser.builder().toolCallingEnabled(true).parseToolCallsFromText(true).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.fromText("transferToAgent(\"story_phase_3_draft\")")))
            .build();

    final Content parsed = parser.parse(content);

    final List<Part> parts = parsed.parts().orElse(List.of());
    final List<Part> functionParts =
        parts.stream().filter(part -> part.functionCall().isPresent()).toList();
    assertThat(functionParts).hasSize(1);
    assertThat(functionParts.getFirst().functionCall().orElseThrow().name())
        .hasValue("transfer_to_agent");
    assertThat(functionParts.getFirst().functionCall().orElseThrow().args().orElseThrow())
        .containsEntry("agent_name", "story_phase_3_draft");
  }

  @Test
  void shouldStripFunctionStyleTransferCallsFromTextPart() {
    final Parser parser =
        Parser.builder().toolCallingEnabled(true).parseToolCallsFromText(true).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.fromText(
                        "Plan created.\ntransfer_to_agent(\"story_phase_1_brief\")\nProceeding.")))
            .build();

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    final List<Part> textParts = parts.stream().filter(part -> part.text().isPresent()).toList();
    assertThat(textParts).hasSize(1);
    assertThat(textParts.getFirst().text().orElseThrow()).doesNotContain("transfer_to_agent(");

    final List<Part> functionParts =
        parts.stream().filter(part -> part.functionCall().isPresent()).toList();
    assertThat(functionParts).hasSize(1);
    assertThat(functionParts.getFirst().functionCall().orElseThrow().args().orElseThrow())
        .containsEntry("agent_name", "story_phase_1_brief");
  }

  @Test
  void shouldNotParseNonTransferFunctionStyleCallsFromText() {
    final Parser parser =
        Parser.builder().toolCallingEnabled(true).parseToolCallsFromText(true).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.fromText("run_cmd(\"echo hi\")")))
            .build();

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    final List<Part> functionParts =
        parts.stream().filter(part -> part.functionCall().isPresent()).toList();
    assertThat(functionParts).isEmpty();
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().text()).hasValue("run_cmd(\"echo hi\")");
  }
}
