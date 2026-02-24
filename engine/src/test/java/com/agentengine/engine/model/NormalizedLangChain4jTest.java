package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

class NormalizedLangChain4jTest {

  @Test
  void streamingResponsesUseModelRole() {
    final ChatModel chatModel = new StubChatModel();
    final StreamingChatModel streamingChatModel = new StubStreamingChatModel();
    final NormalizedLangChain4j model =
        new NormalizedLangChain4j(chatModel, streamingChatModel, "test-model");
    final LlmRequest request =
        LlmRequest.builder().contents(List.of(Content.fromParts(Part.fromText("Hi")))).build();

    final List<LlmResponse> responses = model.generateContent(request, true).toList().blockingGet();

    assertThat(responses).isNotEmpty();
    for (final LlmResponse response : responses) {
      assertThat(response.content()).isPresent();
      assertThat(response.content().orElseThrow().role()).contains("model");
    }
  }

  @Test
  void toolCallsAreMarkedAsFinalAndPrecedingTextIsEmitted() {
    final ChatModel chatModel = new StubChatModel();
    final StubStreamingChatModel streamingChatModel = new StubStreamingChatModel();
    streamingChatModel.setResponses(
        List.of("Partial", " text "),
        ChatResponse.builder()
            .aiMessage(AiMessage.from(List.of(
                dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                    .id("call-id")
                    .name("test_tool")
                    .arguments("{}")
                    .build())))
            .build());

    final NormalizedLangChain4j model =
        new NormalizedLangChain4j(chatModel, streamingChatModel, "test-model");
    final LlmRequest request =
        LlmRequest.builder().contents(List.of(Content.fromParts(Part.fromText("Hi")))).build();

    final List<LlmResponse> responses = model.generateContent(request, true).toList().blockingGet();

    // Expecting:
    // 1. "Partial" (partial)
    // 2. " text " (partial)
    // 3. "Partial text " (partial) - emitted right before tool call to ensure consistency
    // 4. Tool call (non-partial)
    assertThat(responses).hasSize(4);
    
    // First three are partial chunks
    assertThat(responses.get(0).partial()).contains(true);
    assertThat(responses.get(1).partial()).contains(true);
    assertThat(responses.get(2).partial()).contains(true);
    assertThat(responses.get(2).content().get().text()).isEqualTo("Partial text ");
    
    // Fourth is the tool call
    final com.google.genai.types.Part toolPart = responses.get(3).content().get().parts().get().get(0);
    assertThat(toolPart.functionCall()).isPresent();
    assertThat(toolPart.functionCall().get().id()).isPresent();
    assertThat(toolPart.functionCall().get().id().get()).startsWith("adk-");
  }

  private static final class StubChatModel implements ChatModel {
    @Override
    public ChatResponse doChat(final ChatRequest chatRequest) {
      return ChatResponse.builder().aiMessage(new AiMessage("ok")).build();
    }
  }

  private static final class StubStreamingChatModel implements StreamingChatModel {
    private List<String> partials = List.of("Hello", " world");
    private ChatResponse complete = ChatResponse.builder().aiMessage(new AiMessage("done")).build();

    void setResponses(List<String> partials, ChatResponse complete) {
      this.partials = partials;
      this.complete = complete;
    }

    @Override
    public void doChat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
      for (String p : partials) {
        handler.onPartialResponse(p);
      }
      handler.onCompleteResponse(complete);
    }
  }
}
