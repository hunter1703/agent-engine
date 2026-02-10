package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.utils.FinalAnswerAndToolCorrection;
import com.agentengine.engine.utils.Parser;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DelegatingLLMModelTest {

  @Test
  void exposesProtocolAndProcessors() {
    final ChatModel chatModel = mock(ChatModel.class);
    final ChatRequestParameters params = mock(ChatRequestParameters.class);
    when(chatModel.defaultRequestParameters()).thenReturn(params);
    when(params.modelName()).thenReturn("test-model");

    final Parser parser = Parser.create();
    final StreamingChatModel streamingChatModel = mock(StreamingChatModel.class);
    final DelegatingLLMModel model = new DelegatingLLMModel(
        new LangChain4j(chatModel, streamingChatModel, "test-model"), parser, "protocol", true, false);

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
  void stripsToolSpecificationsWhenParsingFromText() {
    final ChatModel chatModel = mock(ChatModel.class);
    final ChatRequestParameters params = mock(ChatRequestParameters.class);
    when(chatModel.defaultRequestParameters()).thenReturn(params);
    when(params.modelName()).thenReturn("test-model");

    final StreamingChatModel streamingChatModel = mock(StreamingChatModel.class);
    when(chatModel.chat(any(ChatRequest.class)))
        .thenReturn(ChatResponse.builder().aiMessage(new AiMessage("ok")).build());

    final Parser parser = Parser.create().toolCallingEnabled(true).parseToolCallsFromText(true);
    final DelegatingLLMModel model = new DelegatingLLMModel(
        new LangChain4j(chatModel, streamingChatModel, "test-model"), parser, "protocol", true, true);

    final Schema parameters = Schema.builder().type(Type.Known.OBJECT)
        .properties(Map.of("command", Schema.builder().type(Type.Known.STRING).build())).required(List.of("command"))
        .build();
    final FunctionDeclaration declaration = FunctionDeclaration.builder().name("run_cmd").description("Run command")
        .parameters(parameters).build();
    final BaseTool tool = new BaseTool("run_cmd", "Run command") {
      @Override
      public Optional<FunctionDeclaration> declaration() {
        return Optional.of(declaration);
      }
    };

    final LlmRequest request = LlmRequest.builder().contents(List.of(Content.fromParts(Part.fromText("hello"))))
        .appendTools(List.of(tool)).build();

    model.generateContent(request, false).blockingFirst();

    final ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
    verify(chatModel).chat(captor.capture());
    assertThat(captor.getValue().toolSpecifications()).isEmpty();
  }

  @Test
  void doesNotStreamWhenParsingToolCallsFromText() {
    final ChatModel chatModel = mock(ChatModel.class);
    final ChatRequestParameters params = mock(ChatRequestParameters.class);
    when(chatModel.defaultRequestParameters()).thenReturn(params);
    when(params.modelName()).thenReturn("test-model");
    when(chatModel.chat(any(ChatRequest.class)))
        .thenReturn(ChatResponse.builder().aiMessage(new AiMessage("ok")).build());

    final StreamingChatModel streamingChatModel = mock(StreamingChatModel.class);
    final Parser parser = Parser.create().toolCallingEnabled(true).parseToolCallsFromText(true);
    final DelegatingLLMModel model = new DelegatingLLMModel(
        new LangChain4j(chatModel, streamingChatModel, "test-model"), parser, "protocol", true, true);

    final LlmRequest request = LlmRequest.builder().contents(List.of(Content.fromParts(Part.fromText("hello"))))
        .build();

    model.generateContent(request, true).blockingFirst();

    verify(chatModel).chat(any(ChatRequest.class));
    verify(streamingChatModel, never()).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
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

    final DelegatingLLMModel model = new DelegatingLLMModel(
        new LangChain4j(chatModel, streamingChatModel, "test-model"), Parser.create(), "protocol", true, false);
    final LlmRequest request = LlmRequest.builder().contents(List.of(Content.fromParts(Part.fromText("hello"))))
        .build();
    final List<LlmResponse> responses = model.generateContent(request, true).toList().blockingGet();

    assertThat(responses).isNotEmpty();
    verify(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
  }

  @Test
  void streamsWhenToolCallingDisabled() {
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

    final DelegatingLLMModel model = new DelegatingLLMModel(
        new LangChain4j(chatModel, streamingChatModel, "test-model"), Parser.create(), "protocol", false, true);
    final LlmRequest request = LlmRequest.builder().contents(List.of(Content.fromParts(Part.fromText("hello"))))
        .build();
    final List<LlmResponse> responses = model.generateContent(request, true).toList().blockingGet();

    assertThat(responses).isNotEmpty();
    verify(streamingChatModel).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
  }
}
