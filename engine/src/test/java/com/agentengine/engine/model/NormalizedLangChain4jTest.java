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

  private static final class StubChatModel implements ChatModel {
    @Override
    public ChatResponse doChat(final ChatRequest chatRequest) {
      return ChatResponse.builder().aiMessage(new AiMessage("ok")).build();
    }
  }

  private static final class StubStreamingChatModel implements StreamingChatModel {
    @Override
    public void doChat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
      handler.onPartialResponse("Hello");
      handler.onPartialResponse(" world");
      handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("done")).build());
    }
  }
}
