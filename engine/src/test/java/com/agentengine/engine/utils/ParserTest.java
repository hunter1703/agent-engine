package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.List;
import java.util.Map;
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

  @Test
  void stripsToolCallTagsWhenToolCallingEnabled() {
    final Parser parser =
        Parser.create()
            .withResponseFormat(ResponseFormatType.TEXT)
            .toolCallingEnabled(true)
            .parseToolCallsFromText(false);
    final Content content = Content.fromParts(Part.fromText("Answer <tool_call>"));

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().text().orElse("")).isEqualTo("Answer");
  }

  @Test
  void preservesToolPartsWhenNativeToolCalling() {
    final Parser parser = Parser.create().toolCallingEnabled(true).parseToolCallsFromText(false);
    final FunctionCall functionCall =
        FunctionCall.builder().id("call-1").name("run_cmd").args(Map.of("command", "ls")).build();
    final FunctionResponse functionResponse =
        FunctionResponse.builder()
            .id("call-1")
            .name("run_cmd")
            .response(Map.of("output", "ok"))
            .build();
    final Content callContent =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.builder().text("run").build(),
                    Part.builder().functionCall(functionCall).build()))
            .build();
    final Content responseContent =
        Content.builder()
            .role("user")
            .parts(List.of(Part.builder().functionResponse(functionResponse).build()))
            .build();
    final LlmRequest request =
        LlmRequest.builder().contents(List.of(callContent, responseContent)).build();

    final LlmRequest updated = parser.processRequest(null, request).blockingGet().updatedRequest();

    final List<Content> updatedContents = updated.contents();
    assertThat(updatedContents).hasSize(2);
    final List<Part> updatedCallParts = updatedContents.getFirst().parts().orElse(List.of());
    assertThat(updatedCallParts).anyMatch(part -> part.functionCall().isPresent());
    assertThat(updatedCallParts).anyMatch(part -> "run".equals(part.text().orElse("")));

    final List<Part> updatedResponseParts = updatedContents.get(1).parts().orElse(List.of());
    assertThat(updatedResponseParts).hasSize(1);
    assertThat(updatedResponseParts).anyMatch(part -> part.functionResponse().isPresent());
  }

  @Test
  void skipsEmptyJsonPartsForToolResponses() {
    final Parser parser =
        Parser.create()
            .withResponseFormat(ResponseFormatType.JSON)
            .toolCallingEnabled(true)
            .parseToolCallsFromText(false);
    final FunctionResponse functionResponse =
        FunctionResponse.builder()
            .id("call-1")
            .name("run_cmd")
            .response(Map.of("output", "ok"))
            .build();
    final Content responseContent =
        Content.builder()
            .role("user")
            .parts(List.of(Part.builder().functionResponse(functionResponse).build()))
            .build();
    final LlmRequest request = LlmRequest.builder().contents(List.of(responseContent)).build();

    final LlmRequest updated = parser.processRequest(null, request).blockingGet().updatedRequest();

    final List<Content> updatedContents = updated.contents();
    assertThat(updatedContents).hasSize(1);
    final List<Part> updatedParts = updatedContents.getFirst().parts().orElse(List.of());
    assertThat(updatedParts).hasSize(1);
    assertThat(updatedParts.getFirst().functionResponse()).isPresent();
    assertThat(updatedParts.getFirst().text()).isEmpty();
  }

  @Test
  void mergesNativeAndTextToolCalls() {
    final Parser parser = Parser.create().toolCallingEnabled(true).parseToolCallsFromText(true);
    final FunctionCall nativeCall =
        FunctionCall.builder().id("call-native").name("native_tool").args(Map.of()).build();
    final String textWithCall = "{'id': 'call-text', 'name': 'text_tool', 'args': {}}";
    final Content content =
        Content.builder()
            .role("model")
            .parts(
                List.of(
                    Part.builder().text(textWithCall).build(),
                    Part.builder().functionCall(nativeCall).build()))
            .build();

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    // Expecting 3 parts:
    // 1. Native call
    // 2. Text-based call
    // 3. (Optional) text part (empty since textWithCall was stripped)
    assertThat(parts).anyMatch(p -> p.functionCall().isPresent() && "native_tool".equals(p.functionCall().get().name().orElse("")));
    assertThat(parts).anyMatch(p -> p.functionCall().isPresent() && "text_tool".equals(p.functionCall().get().name().orElse("")));
  }
}
