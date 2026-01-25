package com.agentengine.engine.model;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.utils.AgentUtils;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.List;

public final class LangChain4JLLMModel implements LLMModel {
  private final ChatLanguageModel model;
  private final ResponseFormat responseFormat;
  private final boolean thoughtsEnabled;
  private final String thoughtsStartTag;
  private final String thoughtsEndTag;
  private final ContextManager contextManager;

  public LangChain4JLLMModel(final ChatLanguageModel model, final ResponseFormat responseFormat,
      final boolean thoughtsEnabled, final String thoughtsStartTag, final String thoughtsEndTag,
      ContextManager contextManager) {
    this.model = model;
    this.responseFormat = responseFormat;
    this.thoughtsEnabled = thoughtsEnabled;
    this.thoughtsStartTag = thoughtsStartTag;
    this.thoughtsEndTag = thoughtsEndTag;
    this.contextManager = contextManager;
  }

  @Override
  public Message generate(final List<Message> messages) {
    final List<ChatMessage> prompt = new ArrayList<>();
    for (Message message : messages) {
      prompt.add(toChatMessage(message));
    }
    final ChatResponse response = model.chat(prompt);
    final AiMessage aiMessage = response.aiMessage();
    return Message.assistant(aiMessage.text(), AgentUtils.transformToToolCalls(aiMessage.toolExecutionRequests()));
  }

  @Override
  public ResponseFormatType responseFormat() {
    return responseFormat.type() == dev.langchain4j.model.chat.request.ResponseFormatType.JSON
        ? ResponseFormatType.JSON
        : ResponseFormatType.TEXT;
  }

  @Override
  public boolean thoughtsEnabled() {
    return thoughtsEnabled;
  }

  @Override
  public String thoughtsStartTag() {
    return thoughtsStartTag;
  }

  @Override
  public String thoughtsEndTag() {
    return thoughtsEndTag;
  }

  @Override
  public ContextManager getContextManager() {
    return contextManager;
  }

  private ChatMessage toChatMessage(final Message message) {
    final String content = message.getContent();
    if (message.getRole() == Role.SYSTEM) {
      return SystemMessage.from(content);
    }
    if (message.getRole() == Role.USER) {
      return UserMessage.from(content);
    }
    return AiMessage.from(content == null ? "" : content);
  }
}
