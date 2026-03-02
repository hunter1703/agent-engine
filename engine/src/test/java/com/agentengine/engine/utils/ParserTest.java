package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.Parser;

import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.adk.sessions.Session;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ParserTest {

  @Test
  void keepsThoughtTagsInPlainTextResponses() {
    final Parser parser = Parser.builder()
        .withResponseFormat(ResponseFormatType.TEXT)
        .build();
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
        Parser.builder()
            .withResponseFormat(ResponseFormatType.TEXT)
            .toolCallingEnabled(true)
            .parseToolCallsFromText(false)
            .build();
    final Content content = Content.fromParts(Part.fromText("Answer <tool_call>"));

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().text().orElse("")).isEqualTo("Answer <tool_call>");
  }

  @Test
  void preservesToolPartsWhenNativeToolCalling() {
    final Parser parser = Parser.builder()
        .toolCallingEnabled(true)
        .parseToolCallsFromText(false)
        .build();
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
        Parser.builder()
            .withResponseFormat(ResponseFormatType.JSON)
            .toolCallingEnabled(true)
            .parseToolCallsFromText(false)
            .build();
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
  void prioritizesTextToolCallsOverNativeWhenEnabled() {
    final Parser parser = Parser.builder()
        .toolCallingEnabled(true)
        .parseToolCallsFromText(true)
        .build();
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

    // Native call should be dropped in favor of text parsing
    assertThat(parts).noneMatch(p -> p.functionCall().isPresent() && "native_tool".equals(p.functionCall().get().name().orElse("")));
    assertThat(parts).anyMatch(p -> p.functionCall().isPresent() && "text_tool".equals(p.functionCall().get().name().orElse("")));
  }

  @Test
  void extractsThoughtTags() {
    final Parser parser = Parser.builder()
        .withResponseFormat(ResponseFormatType.TEXT)
        .build();
    final String response = "<thought>Thinking about it</thought>Final answer here";
    final Content content = Content.fromParts(Part.fromText(response));

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    // Expecting 2 parts:
    // 1. Text part: "Final answer here"
    // 2. Thought part: "Thinking about it"
    assertThat(parts).hasSize(2);
    assertThat(parts).anyMatch(p -> "Final answer here".equals(p.text().orElse("")) && !p.thought().orElse(false));
    assertThat(parts).anyMatch(p -> "Thinking about it".equals(p.text().orElse("")) && p.thought().orElse(true));
  }


  @Test
  void splitsMixedPartsIntoSeparateTypes() {
    final Parser parser = Parser.builder()
        .toolCallingEnabled(true)
        .parseToolCallsFromText(false)
        .build();
    final FunctionCall functionCall =
        FunctionCall.builder().id("call-1").name("run_cmd").args(Map.of("command", "ls")).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.builder().text("hello").functionCall(functionCall).build()))
            .build();

    final Content parsed = parser.parse(content);
    final List<Part> parts = parsed.parts().orElse(List.of());

    assertThat(parts).anyMatch(part -> part.functionCall().isPresent() && part.text().isEmpty());
    assertThat(parts).anyMatch(part -> "hello".equals(part.text().orElse("")) && part.functionCall().isEmpty());
    assertThat(parts)
        .noneMatch(part -> part.functionCall().isPresent() && part.text().isPresent());
  }

  @Test
  void stripsToolCallsFromPartialResponses() {
    final Parser parser = Parser.builder()
        .toolCallingEnabled(true)
        .parseToolCallsFromText(false)
        .build();
    final FunctionCall functionCall =
        FunctionCall.builder().id("call-1").name("run_cmd").args(Map.of("command", "ls")).build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.builder().text("hello").functionCall(functionCall).build()))
            .build();
    final var response = com.google.adk.models.LlmResponse.builder()
        .content(content)
        .partial(true)
        .turnComplete(true)
        .build();

    final Session session = Session.builder("s1")
        .appName("app")
        .userId("u1")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
    final InvocationContext context = InvocationContext.builder()
        .session(session)
        .invocationId("inv-1")
        .build();

    final var updated = parser.processResponse(context, response).blockingGet().updatedResponse();

    final List<Part> parts = updated.content().orElseThrow().parts().orElse(List.of());
    assertThat(parts).hasSize(1);
    assertThat(parts.getFirst().functionCall()).isEmpty();
    assertThat(parts.getFirst().text().orElse("")).isEqualTo("hello");
    assertThat(updated.partial().orElse(false)).isTrue();
    assertThat(updated.turnComplete().orElse(false)).isTrue();
    assertThat(RunStateUtils.getState(context).violations())
        .extracting(Violation::getCode)
        .contains("partial_tool_calls");
  }
}
