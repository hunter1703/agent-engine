package com.agentengine.engine.model;

import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.utils.ModelUtils;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DelegatingLLMModel extends AbstractLLM {
  private static final Logger LOG = LoggerFactory.getLogger(DelegatingLLMModel.class);
  private final BaseLlm delegate;
  private final Parser parser;

  public DelegatingLLMModel(
      final BaseLlm delegate,
      final Parser parser,
      final String protocol,
      final boolean toolCallingEnabled,
      final boolean parseToolCallsFromText) {
    super(
        Objects.requireNonNull(delegate, "delegate cannot be null").model(),
        parser,
        protocol,
        toolCallingEnabled,
        parseToolCallsFromText);
    this.delegate = delegate;
    this.parser = parser;
  }

  @Override
  public Parser getParser() {
    return parser;
  }

  @Override
  public Flowable<LlmResponse> generateContent(final LlmRequest llmRequest, final boolean stream) {
    final LlmRequest requestForModel = parser.normalizeRequest(llmRequest);
    final boolean useStreaming = stream && !isParseToolCallsFromText();
    LOG.debug(
        "Delegating LLM generateContent using {} mode",
        useStreaming ? "streaming" : "non-streaming");
    final Flowable<LlmResponse> normalizedResponses = delegate.generateContent(requestForModel, useStreaming).map(this::normalizeResponse);
    if (!useStreaming) {
      return normalizedResponses.map(ModelUtils::markTurnComplete);
    }
    return normalizedResponses;
  }

  @Override
  public BaseLlmConnection connect(final LlmRequest llmRequest) {
    return delegate.connect(llmRequest);
  }

  private LlmResponse normalizeResponse(final LlmResponse response) {
    if (response == null || response.content().isEmpty()) {
      return response;
    }
    final Content content = response.content().orElse(null);
    if (content == null) {
      return response;
    }
    try {
      final Content normalized = parser.parse(content);
      if (normalized == null) {
        return response;
      }
      return response.toBuilder().content(normalized).build();
    } catch (Exception ex) {
      LOG.debug("Failed to normalize model response; returning raw response.", ex);
      return response;
    }
  }
}
