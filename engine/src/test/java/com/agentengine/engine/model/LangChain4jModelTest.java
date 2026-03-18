package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LangChain4jModelTest {

  @Test
  void shouldEmitStreamingChunksAsPartialWithModelRole() {
    final var model = new LangChain4jModel(null, streamingModel("Hel", "lo"), "test");
    final var responses = model.generateContent(minimalRequest(), true).toList().blockingGet();

    assertThat(responses).hasSize(3); // 2 partial chunks + 1 final assembled
    assertThat(responses.getFirst().partial()).contains(true);
    assertThat(responses.getFirst().content().orElseThrow().role()).contains("model");

    final var last = responses.getLast();
    assertThat(last.partial()).contains(false);
    assertThat(last.content().orElseThrow().role()).contains("model");
    assertThat(last.content().orElseThrow().text()).isEqualTo("Hello");
  }

  @Test
  void shouldIncludeTextAndToolCallsTogetherInFinalStreamingResponse() {
    final var model = new LangChain4jModel(null, streamingModelWithToolCall("Searching...", "search", "id1"), "test");
    final var responses = model.generateContent(minimalRequest(), true).toList().blockingGet();

    // 1 partial chunk + 1 final with both text and FC
    assertThat(responses).hasSize(2);
    final var last = responses.getLast();
    assertThat(last.partial()).contains(false);
    final var parts = last.content().orElseThrow().parts().orElseThrow();
    assertThat(parts.getFirst().text()).contains("Searching...");
    assertThat(parts.getLast().functionCall()).isPresent();
  }

  @Test
  void shouldOmitTextFromAssistantMessageWhenToolCallsPresent() {
    final List<ChatRequest> captured = new ArrayList<>();
    final var model = new LangChain4jModel(capturingChatModel(captured), null, "test");

    // Session history: model turn with both text and a function call
    final var modelTurn = Content.builder().role("model")
        .parts(List.of(Part.fromText("Let me search for that"),
            Part.builder().functionCall(FunctionCall.builder().id("fc1").name("search").args(Map.of("q", "weather")).build()).build()))
        .build();

    model.generateContent(requestWithContents(modelTurn), false).blockingFirst();

    assertThat(captured).hasSize(1);
    final var aiMessage = (AiMessage) captured.getFirst().messages().getFirst();
    assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
    assertThat(aiMessage.text()).isNullOrEmpty();
  }

  // --- Stubs ---

  private static StreamingChatModel streamingModel(final String... tokens) {
    return new StreamingChatModel() {
      @Override
      public void doChat(final ChatRequest request, final StreamingChatResponseHandler handler) {
        for (final String token : tokens) {
          handler.onPartialResponse(token);
        }
        handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.builder().text(String.join("", tokens)).build()).build());
      }
    };
  }

  private static StreamingChatModel streamingModelWithToolCall(final String text, final String toolName, final String toolId) {
    return new StreamingChatModel() {
      @Override
      public void doChat(final ChatRequest request, final StreamingChatResponseHandler handler) {
        handler.onPartialResponse(text);
        handler.onCompleteResponse(ChatResponse.builder()
            .aiMessage(AiMessage.builder().text(text)
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder().id(toolId).name(toolName).arguments("{}").build())).build())
            .build());
      }
    };
  }

  private static ChatModel capturingChatModel(final List<ChatRequest> captured) {
    return new ChatModel() {
      @Override
      public ChatResponse doChat(final ChatRequest request) {
        captured.add(request);
        return ChatResponse.builder().aiMessage(AiMessage.builder().text("ok").build()).build();
      }
    };
  }

  private static LlmRequest minimalRequest() {
    return LlmRequest.builder().model("test").contents(List.of(Content.builder().role("user").parts(Part.fromText("hi")).build()))
        .appendTools(List.of()).build();
  }

  private static LlmRequest requestWithContents(final Content... contents) {
    return LlmRequest.builder().model("test").contents(List.of(contents)).appendTools(List.of()).build();
  }
}
