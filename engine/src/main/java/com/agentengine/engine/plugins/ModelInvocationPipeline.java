package com.agentengine.engine.plugins;

import static com.google.genai.types.FinishReason.Known.STOP;

import com.agentengine.engine.agents.processors.request.CorrectionProcessor;
import com.agentengine.engine.agents.processors.request.PlanningRequestProcessor;
import com.agentengine.engine.agents.processors.response.PartOrderingResponseProcessor;
import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.agents.processors.response.ToolCallSanitizationResponseProcessor;
import com.agentengine.engine.agents.processors.response.TurnCompletionResponseProcessor;
import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.FinishReason;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Optional;

/** Executes request/response transformations around each model call. */
public final class ModelInvocationPipeline extends BasePlugin {
  private static final String NAME = "model_invocation_pipeline";

  public ModelInvocationPipeline() {
    super(NAME);
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder requestBuilder) {
    final InvocationContext invocationContext =
        callbackContext == null ? null : callbackContext.invocationContext();
    LlmRequest request = requestBuilder.build();
    request = applyRequestProcessor(CorrectionProcessor.INSTANCE, invocationContext, request);
    request = applyRequestProcessor(PlanningRequestProcessor.INSTANCE, invocationContext, request);
    requestBuilder.contents(request.contents());
    return Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse llmResponse) {
    final InvocationContext invocationContext =
        callbackContext == null ? null : callbackContext.invocationContext();
    LlmResponse response = llmResponse;
    response =
        applyResponseProcessor(
            ToolCallSanitizationResponseProcessor.INSTANCE, invocationContext, response);
    response =
        applyResponseProcessor(PlanLoopResponseProcessor.INSTANCE, invocationContext, response);
    response =
        applyResponseProcessor(
            TurnCompletionResponseProcessor.INSTANCE, invocationContext, response);
    response =
        applyResponseProcessor(PartOrderingResponseProcessor.INSTANCE, invocationContext, response);
    final boolean isRunFinished =
        response.turnComplete().orElse(false) && !ToolUtils.hasToolParts(response);
    final FinishReason finishReason =
        isRunFinished ? response.finishReason().orElse(new FinishReason(STOP)) : null;
    return Maybe.just(response.toBuilder().finishReason(Optional.ofNullable(finishReason)).build());
  }

  private static LlmRequest applyRequestProcessor(
      final RequestProcessor processor,
      final InvocationContext invocationContext,
      final LlmRequest request) {
    if (processor == null) {
      return request;
    }
    return processor.processRequest(invocationContext, request).blockingGet().updatedRequest();
  }

  private static LlmResponse applyResponseProcessor(
      final ResponseProcessor processor,
      final InvocationContext invocationContext,
      final LlmResponse response) {
    if (processor == null) {
      return response;
    }
    return processor.processResponse(invocationContext, response).blockingGet().updatedResponse();
  }
}
