package com.agentengine.engine.model;

import com.agentengine.engine.utils.FinalAnswerAndToolCorrection;
import com.agentengine.engine.utils.Parser;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.langchain4j.LangChain4j;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;

public class LangChain4JLLMModel extends LangChain4j {
  private final Parser parser;
  private final String protocol;
  private final boolean toolCallingEnabled;
  private final boolean parseToolCallsFromText;

  public LangChain4JLLMModel(ChatModel chatModel, StreamingChatModel streamingChatModel, Parser parser, String protocol,
      boolean toolCallingEnabled, boolean parseToolCallsFromText) {
    super(chatModel, streamingChatModel, protocol);
    this.parser = parser;
    this.protocol = protocol;
    this.toolCallingEnabled = toolCallingEnabled;
    this.parseToolCallsFromText = parseToolCallsFromText;
  }

  public String getProtocol() {
    return protocol;
  }

  public List<RequestProcessor> getRequestProcessors() {
    return List.of(FinalAnswerAndToolCorrection.builder().convertToThought(true).build(), parser);
  }

  public List<ResponseProcessor> getResponseProcessors() {
    return List.of(parser, FinalAnswerAndToolCorrection.builder().convertToThought(true).build());
  }

  @Override
  public Flowable<LlmResponse> generateContent(final LlmRequest llmRequest, final boolean stream) {
    if (!stream) {
      return super.generateContent(llmRequest, false).map(LangChain4JLLMModel::markTurnComplete);
    }
    if (!toolCallingEnabled || !parseToolCallsFromText) {
      return super.generateContent(llmRequest, true);
    }
    return super.generateContent(llmRequest, false).map(LangChain4JLLMModel::markTurnComplete);
  }

  public boolean isToolCallingEnabled() {
    return toolCallingEnabled;
  }

  public boolean isParseToolCallsFromText() {
    return parseToolCallsFromText;
  }

  private static LlmResponse markTurnComplete(final LlmResponse response) {
    if (response == null) {
      return null;
    }
    return response.toBuilder().turnComplete(true).build();
  }
}
