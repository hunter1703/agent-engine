package com.agentengine.engine.model;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.Tool;
import com.agentengine.engine.utils.AgentUtils;
import com.agentengine.engine.utils.MessageParser;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.agentengine.engine.tools.ToolUtils.buildToolSpecifications;

public final class LangChain4JLLMModel implements LLMModel {
  private static final Logger LOG = LoggerFactory.getLogger(LangChain4JLLMModel.class);
  private final ChatLanguageModel model;
  private final ResponseFormat responseFormat;
  private final boolean thoughtsEnabled;
  private final String thoughtsStartTag;
  private final String thoughtsEndTag;
  private final ContextManager contextManager;
  private final List<ToolSpecification> toolSpecifications;
  private final MessageParser messageParser;

  public LangChain4JLLMModel(final ChatLanguageModel model, final ResponseFormat responseFormat,
      final boolean thoughtsEnabled, final String thoughtsStartTag, final String thoughtsEndTag,
      final ContextManager contextManager, final List<Tool> tools, final boolean parseToolCallsFromText) {
    this.model = model;
    this.responseFormat = responseFormat;
    this.thoughtsEnabled = thoughtsEnabled;
    this.thoughtsStartTag = thoughtsStartTag;
    this.thoughtsEndTag = thoughtsEndTag;
    this.contextManager = contextManager;
    this.toolSpecifications = buildToolSpecifications(tools);
    this.messageParser = MessageParser.create().withResponseFormat(responseFormat())
        .toolCallingAllowed(CollectionUtils.isNotEmpty(tools)).parseToolCallsFromText(parseToolCallsFromText)
        .areThoughtsEnabled(thoughtsEnabled).withThoughtsStartTag(thoughtsStartTag).withThoughtsEndTag(thoughtsEndTag);
  }

  @Override
  public Message generate(final List<Message> messages) {
    final List<ChatMessage> prompt = new ArrayList<>();
    for (Message message : messages) {
      prompt.add(toChatMessage(message));
    }

    final ChatResponse response;
    ChatRequest.Builder builder = ChatRequest.builder().messages(prompt).responseFormat(responseFormat);
    if (CollectionUtils.isNotEmpty(toolSpecifications)) {
      builder = builder.toolSpecifications(toolSpecifications);
    }
    response = model.chat(builder.build());

    final AiMessage aiMessage = response.aiMessage();

    final Message assistantMessage = Message.assistant(aiMessage.text(), null,
        AgentUtils.transformToToolCalls(aiMessage.toolExecutionRequests()));
    return messageParser.parse(assistantMessage);
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
