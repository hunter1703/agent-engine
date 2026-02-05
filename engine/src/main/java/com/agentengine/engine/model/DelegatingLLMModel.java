package com.agentengine.engine.model;

import com.agentengine.engine.utils.Parser;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Objects;

public final class DelegatingLLMModel extends AbstractLLM {
  private final BaseLlm delegate;

  public DelegatingLLMModel(final BaseLlm delegate, final Parser parser, final String protocol,
      final boolean toolCallingEnabled, final boolean parseToolCallsFromText) {
    super(Objects.requireNonNull(delegate, "delegate cannot be null").model(), parser, protocol, toolCallingEnabled,
        parseToolCallsFromText);
    this.delegate = delegate;
  }

  @Override
  public Flowable<LlmResponse> generateContent(final LlmRequest llmRequest, final boolean stream) {
    final LlmRequest requestForModel = LlmModelUtils.stripToolsFromModelRequest(llmRequest, isToolCallingEnabled(),
        isParseToolCallsFromText());
    if (!stream || (isToolCallingEnabled() && isParseToolCallsFromText())) {
      return delegate.generateContent(requestForModel, false).map(LlmModelUtils::markTurnComplete);
    }
    return delegate.generateContent(requestForModel, true);
  }

  @Override
  public BaseLlmConnection connect(final LlmRequest llmRequest) {
    return delegate.connect(llmRequest);
  }
}
