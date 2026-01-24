package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.state.InMemoryStateStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LangChain4JLLMModelTest {

  @Test
  void generateMapsMessagesToChatPromptAndReturnsAssistant() {
    ChatLanguageModel chatModel = mock(ChatLanguageModel.class);
    ChatResponse response = mock(ChatResponse.class);
    AiMessage aiMessage = mock(AiMessage.class);

    when(aiMessage.text()).thenReturn("hello");
    when(response.aiMessage()).thenReturn(aiMessage);
    when(chatModel.chat(anyList())).thenReturn(response);

    ContextManager contextManager =
        new BaseContextManager(new InMemoryStateStore(), "system", "protocol", List.of());
    LangChain4JLLMModel model = new LangChain4JLLMModel(chatModel,
        new ResponseFormat.Builder().type(dev.langchain4j.model.chat.request.ResponseFormatType.TEXT).build(), true,
        "<think>", "</think>",
        contextManager);

    List<Message> prompt = List.of(Message.system("sys"), Message.user("hi"), Message.assistant("prior"));

    Message result = model.generate(prompt);

    assertThat(result.getContent()).isEqualTo("hello");
    assertThat(result.getThoughts()).isNull();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ChatMessage>> captor = (ArgumentCaptor<List<ChatMessage>>) (ArgumentCaptor<?>) ArgumentCaptor
        .forClass(List.class);
    verify(chatModel).chat(captor.capture());
    List<ChatMessage> captured = captor.getValue();
    assertThat(captured.getFirst()).isInstanceOf(SystemMessage.class);
    assertThat(captured.get(1)).isInstanceOf(UserMessage.class);
    assertThat(captured.get(2)).isInstanceOf(AiMessage.class);
  }

  @Test
  void exposesResponseFormatAndThoughtTags() {
    ChatLanguageModel chatModel = mock(ChatLanguageModel.class);
    ContextManager contextManager =
        new BaseContextManager(new InMemoryStateStore(), "system", "protocol", List.of());

    ResponseFormat format = new ResponseFormat.Builder()
        .type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON).build();
    LangChain4JLLMModel model = new LangChain4JLLMModel(chatModel, format, false, "start", "end", contextManager);

    assertThat(model.responseFormat()).isEqualTo(ResponseFormatType.JSON);
    assertThat(model.thoughtsEnabled()).isFalse();
    assertThat(model.thoughtsStartTag()).isEqualTo("start");
    assertThat(model.thoughtsEndTag()).isEqualTo("end");
  }
}
