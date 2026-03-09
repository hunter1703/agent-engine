package com.agentengine.engine.plugins;

import com.agentengine.engine.agents.processors.request.CorrectionProcessor;
import com.agentengine.engine.agents.processors.request.HumanInTheLoopRequestProcessor;
import com.agentengine.engine.agents.processors.request.PlanningRequestProcessor;
import com.agentengine.engine.agents.processors.response.PartOrderingResponseProcessor;
import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.agents.processors.response.ToolCallSanitizationResponseProcessor;
import com.agentengine.engine.agents.processors.response.TurnCompletionResponseProcessor;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.guardrails.GuardrailUtils;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.genai.types.FinishReason.Known.STOP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * App-level plugin that applies all engine request/response semantics across every agent
 * in the agent graph automatically.
 */
public final class EnginePlugin extends BasePlugin {
  private static final Logger LOG = LoggerFactory.getLogger(EnginePlugin.class);
  private static final String NAME = "engine_core";

  private final Map<String, ContextManager> contextManagerByAgentId;

  public EnginePlugin(final Map<String, ContextManager> contextManagerByAgentId) {
    super(NAME);
    this.contextManagerByAgentId = Map.copyOf(contextManagerByAgentId);
  }

  @Override
  public Maybe<Content> beforeAgentCallback(final BaseAgent agent, final CallbackContext ctx) {
    RunStateUtils.initState(ctx.invocationContext());
    return Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder requestBuilder) {
    final InvocationContext invocationContext =
        callbackContext == null ? null : callbackContext.invocationContext();
    LlmRequest request = requestBuilder.build();

    final HITLResult hitlResult = applyHumanInTheLoopProcessor(invocationContext, request);
    if (hitlResult.isInvocationEnded()) {
      return Maybe.just(hitlResult.response());
    }
    request = hitlResult.updatedRequest();

    request = applyRequestProcessor(CorrectionProcessor.INSTANCE, invocationContext, request);
    request = applyRequestProcessor(PlanningRequestProcessor.INSTANCE, invocationContext, request);
    request = applyContextManager(request, invocationContext);

    requestBuilder.contents(request.contents());
    return Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse llmResponse) {
    final InvocationContext invocationContext =
        callbackContext == null ? null : callbackContext.invocationContext();
    LlmResponse response = llmResponse;
    response = applyResponseProcessor(ToolCallSanitizationResponseProcessor.INSTANCE, invocationContext, response);
    response = applyResponseProcessor(PlanLoopResponseProcessor.INSTANCE, invocationContext, response);
    response = applyResponseProcessor(TurnCompletionResponseProcessor.INSTANCE, invocationContext, response);
    response = applyResponseProcessor(PartOrderingResponseProcessor.INSTANCE, invocationContext, response);
    final boolean isRunFinished = response.turnComplete().orElse(false) && !ToolUtils.hasToolParts(response);
    final FinishReason finishReason = isRunFinished
        ? response.finishReason().orElse(new FinishReason(STOP))
        : null;
    return Maybe.just(response.toBuilder().finishReason(Optional.ofNullable(finishReason)).build());
  }

  @Override
  public Maybe<Content> afterAgentCallback(final BaseAgent agent, final CallbackContext ctx) {
    RunStateUtils.clearState(ctx.invocationContext());
    return Maybe.empty();
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

  private static HITLResult applyHumanInTheLoopProcessor(
      final InvocationContext invocationContext, final LlmRequest request) {
    final RequestProcessor.RequestProcessingResult hitlResult = HumanInTheLoopRequestProcessor.INSTANCE.processRequest(invocationContext, request).blockingGet();
    final LlmRequest updatedRequest = hitlResult.updatedRequest();
    if (invocationContext != null && invocationContext.endInvocation()) {
      final String message = extractEventText(hitlResult.events());
      return HITLResult.endRunWithResponse(
          GuardrailUtils.buildGuardrailResponse(
              StringUtils.isNotBlank(message)
                  ? message
                  : "Execution is paused and requires user confirmation."));
    }
    return HITLResult.continueRun(updatedRequest);
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

  private LlmRequest applyContextManager(
      final LlmRequest request, final InvocationContext invocationContext) {
    if (request == null || invocationContext == null) {
      return request;
    }
    final String agentId =
        invocationContext.agent() == null ? null : invocationContext.agent().name();
    final ContextManager contextManager = contextManagerByAgentId.get(agentId);
    if (contextManager == null) {
      return request;
    }
    try {
      final List<Content> contents = CollectionUtils.nullSafeList(request.contents());
      final List<Content> prompt =
          contextManager.buildPrompt(agentId, invocationContext.session().id(), contents);
      return request.toBuilder().contents(prompt).build();
    } catch (Exception ex) {
      LOG.warn(
          "Context manager failed for agent_id={} session_id={}; continuing with unmodified prompt.",
          agentId,
          invocationContext.session() == null ? null : invocationContext.session().id(),
          ex);
      return request;
    }
  }

  private static String extractEventText(final Iterable<Event> events) {
    if (events == null) {
      return "";
    }
    for (final Event event : events) {
      if (event == null || event.content().isEmpty()) {
        continue;
      }
      final String text = event.content().get().text();
      if (StringUtils.isNotBlank(text)) {
        return text;
      }
    }
    return "";
  }

  private record HITLResult(LlmRequest updatedRequest, LlmResponse response) {
    public boolean isInvocationEnded() {
      return response != null;
    }

    public static HITLResult continueRun(final LlmRequest updatedRequest) {
      return new HITLResult(updatedRequest, null);
    }

    public static HITLResult endRunWithResponse(final LlmResponse response) {
      return new HITLResult(null, response);
    }
  }
}
