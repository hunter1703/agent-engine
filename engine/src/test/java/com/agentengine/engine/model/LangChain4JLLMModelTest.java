package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.utils.FinalAnswerAndToolCorrection;
import com.agentengine.engine.utils.Parser;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

class LangChain4JLLMModelTest {

  @Test
  void exposesProtocolAndProcessors() {
    final ChatModel chatModel = mock(ChatModel.class);
    final ChatRequestParameters params = mock(ChatRequestParameters.class);
    when(chatModel.defaultRequestParameters()).thenReturn(params);
    when(params.modelName()).thenReturn("test-model");

    final Parser parser = Parser.create();
    final StreamingChatModel streamingChatModel = mock(StreamingChatModel.class);
    final LangChain4JLLMModel model = new LangChain4JLLMModel(chatModel, streamingChatModel, parser, "protocol", true,
        false);

    assertThat(model.getProtocol()).isEqualTo("protocol");

    final List<RequestProcessor> requestProcessors = model.getRequestProcessors();
    assertThat(requestProcessors).hasSize(2);
    assertThat(requestProcessors.getFirst()).isInstanceOf(FinalAnswerAndToolCorrection.class);
    assertThat(requestProcessors.get(1)).isSameAs(parser);

    final List<ResponseProcessor> responseProcessors = model.getResponseProcessors();
    assertThat(responseProcessors).hasSize(2);
    assertThat(responseProcessors.getFirst()).isSameAs(parser);
    assertThat(responseProcessors.get(1)).isInstanceOf(FinalAnswerAndToolCorrection.class);
  }

  @Test
  void streamsWithStreamingChatModel() {
    final ChatModel chatModel = mock(ChatModel.class);
    final ChatRequestParameters params = mock(ChatRequestParameters.class);
    when(chatModel.defaultRequestParameters()).thenReturn(params);
    when(params.modelName()).thenReturn("test-model");

    final StreamingChatModel streamingChatModel = mock(StreamingChatModel.class);
    doAnswer(invocation -> {
      final StreamingChatResponseHandler handler = invocation.getArgument(1);
      handler.onPartialResponse("partial");
      handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("done")).build());
      return null;
    }).when(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

    final LangChain4JLLMModel model = new LangChain4JLLMModel(chatModel, streamingChatModel, Parser.create(),
        "protocol", true, false);
    final LlmRequest request = LlmRequest.builder().contents(List.of(Content.fromParts(Part.fromText("hello"))))
        .build();
    final List<LlmResponse> responses = model.generateContent(request, true).toList().blockingGet();

    assertThat(responses).isNotEmpty();
    verify(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
  }
}
