package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParserTest {

  @Test
  void parsesToolCallsInsideThoughtsAndNormalizesArgs() {
    final Parser parser = Parser.create().toolCallingEnabled(true).parseToolCallsFromText(true).areThoughtsEnabled(true)
        .parseThoughtsFromText(true).withThoughtsStartTag("<think>").withThoughtsEndTag("</think>");

    final String contentText = "<think>{\"id\":\"1\",\"name\":\"run_cmd\",\"args\":{\"cmd\":\"pwd\"}}</think>";
    final Content content = Content.fromParts(Part.fromText(contentText));

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    final FunctionCall call = parts.stream().map(part -> part.functionCall().orElse(null))
        .filter(functionCall -> functionCall != null).findFirst().orElse(null);
    assertThat(call).isNotNull();
    assertThat(call.name().orElse("")).isEqualTo("run_cmd");
    assertThat(call.args().orElse(Map.of())).containsEntry("command", "pwd");

    final Part answerPart = parts.stream()
        .filter(part -> part.functionCall().isEmpty() && !part.thought().orElse(false)).findFirst().orElseThrow();
    assertThat(answerPart.text().orElse("")).isEmpty();
    assertThat(parts).noneMatch(part -> part.thought().orElse(false) && part.text().orElse("").contains("run_cmd"));
  }
}
