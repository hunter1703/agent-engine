package com.agentengine.agent.infra.model;

import com.agentengine.agent.infra.agents.processors.Parser;
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

  public DelegatingLLMModel(final BaseLlm delegate, final Parser parser) {
    super(Objects.requireNonNull(delegate, "delegate cannot be null").model(), parser);
    this.delegate = delegate;
  }

  @Override
  public Flowable<LlmResponse> generateContent(final LlmRequest llmRequest, final boolean stream) {
    final LlmRequest requestForModel = parser.preProcess(llmRequest);
    LOG.debug(
        "Delegating LLM generateContent using {} mode", stream ? "streaming" : "non-streaming");
    return delegate
        .generateContent(requestForModel, stream)
        .doOnNext(response -> LOG.info("RAW LLM OUTPUT: {}", response))
        .map(parser::postProcess)
        .doOnNext(response -> LOG.info("POST-PROCESSED LLM OUTPUT: {}", response));
  }

  @Override
  public BaseLlmConnection connect(final LlmRequest llmRequest) {
    return delegate.connect(llmRequest);
  }
}
