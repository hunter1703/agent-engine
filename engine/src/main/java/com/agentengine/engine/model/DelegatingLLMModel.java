package com.agentengine.engine.model;

import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.utils.ModelUtils;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
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
    final LlmRequest requestForModel =
        ModelUtils.stripToolsFromModelRequest(
            llmRequest, isToolCallingEnabled(), isParseToolCallsFromText());
    final boolean useStreaming = stream && !isParseToolCallsFromText();
    LOG.debug(
        "Delegating LLM generateContent using {} mode",
        useStreaming ? "streaming" : "non-streaming");
    if (!useStreaming) {
      return delegate.generateContent(requestForModel, false).map(ModelUtils::markTurnComplete);
    }
    return delegate.generateContent(requestForModel, true);
  }

  @Override
  public BaseLlmConnection connect(final LlmRequest llmRequest) {
    return delegate.connect(llmRequest);
  }
}
