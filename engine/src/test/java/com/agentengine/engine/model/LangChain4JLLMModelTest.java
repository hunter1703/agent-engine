package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.state.InMemoryMessageStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LangChain4JLLMModelTest {

  @Test
  void generateMapsMessagesToChatPromptAndReturnsAssistant() {
    final ChatLanguageModel chatModel = mock(ChatLanguageModel.class);
    final ChatResponse response = mock(ChatResponse.class);
    final AiMessage aiMessage = mock(AiMessage.class);

    when(aiMessage.text()).thenReturn("hello");
    when(aiMessage.toolExecutionRequests()).thenReturn(List.of()); // Fix: Prevent NPE by mocking toolExecutionRequests
    when(response.aiMessage()).thenReturn(aiMessage);
    when(chatModel.chat(org.mockito.ArgumentMatchers.any(ChatRequest.class))).thenReturn(response);

    final ContextManager contextManager = new BaseContextManager("reasoning", new InMemoryMessageStore(), "system",
        "protocol", List.of());
    final LangChain4JLLMModel model = new LangChain4JLLMModel(chatModel,
        new ResponseFormat.Builder().type(dev.langchain4j.model.chat.request.ResponseFormatType.TEXT).build(), true,
        "<think>", "</think>", contextManager, List.of(), false);

    final List<Message> prompt = List.of(Message.system("sys"), Message.user("hi"), Message.assistant("prior"));

    final Message result = model.generate(prompt);

    assertThat(result.getContent()).isEqualTo("hello");
    assertThat(result.getThoughts()).isNull();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<ChatRequest> captor = (ArgumentCaptor<ChatRequest>) ArgumentCaptor.forClass(ChatRequest.class);
    verify(chatModel).chat(captor.capture());
    final List<ChatMessage> captured = captor.getValue().messages();
    assertThat(captured.getFirst()).isInstanceOf(SystemMessage.class);
    assertThat(captured.get(1)).isInstanceOf(UserMessage.class);
    assertThat(captured.get(2)).isInstanceOf(AiMessage.class);
  }

  @Test
  void exposesResponseFormatAndThoughtTags() {
    final ChatLanguageModel chatModel = mock(ChatLanguageModel.class);
    final ContextManager contextManager = new BaseContextManager("reasoning", new InMemoryMessageStore(), "system",
        "protocol", List.of());

    final ResponseFormat format = new ResponseFormat.Builder()
        .type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON).build();
    final LangChain4JLLMModel model = new LangChain4JLLMModel(chatModel, format, false, "start", "end", contextManager,
        List.of(), false);

    assertThat(model.responseFormat()).isEqualTo(ResponseFormatType.JSON);
    assertThat(model.thoughtsEnabled()).isFalse();
    assertThat(model.thoughtsStartTag()).isEqualTo("start");
    assertThat(model.thoughtsEndTag()).isEqualTo("end");
  }
}
