package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Optional;

/**
 * Initializes per-run state before any request processing.
 *
 * <p>Responsibilities:
 * - Ensure a {@link com.agentengine.engine.utils.RunState} exists in session storage.
 * - Keep initialization idempotent so repeated requests are safe.
 *
 * <p>Ownership: run-state initialization lifecycle.
 */
public final class RunInitRequestProcessor implements RequestProcessor {
  public static final RunInitRequestProcessor INSTANCE = new RunInitRequestProcessor();

  @Override
  public Single<RequestProcessingResult> processRequest(final InvocationContext context, final LlmRequest request) {
    RunStateUtils.initState(context);
    return Single.just(RequestProcessingResult.create(request, List.of()));
  }
}
