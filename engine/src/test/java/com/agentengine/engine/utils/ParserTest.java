package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParserTest {

  @Test
  void keepsThoughtTagsInPlainTextResponses() {
    final Parser parser = Parser.create().withResponseFormat(ResponseFormatType.TEXT);
    final String response = "<think>internal</think> answer";
    final Content content = Content.fromParts(Part.fromText(response));

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().text()).contains(response);
    assertThat(parts).noneMatch(part -> part.thought().orElse(false));
  }
}
