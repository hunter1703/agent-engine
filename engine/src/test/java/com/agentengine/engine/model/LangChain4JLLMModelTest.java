package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import static org.mockito.Mockito.when;

import com.agentengine.engine.utils.FinalAnswerAndToolCorrection;
import com.agentengine.engine.utils.Parser;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
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
    final LangChain4JLLMModel model = new LangChain4JLLMModel(chatModel, streamingChatModel, parser, "protocol");

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
}
