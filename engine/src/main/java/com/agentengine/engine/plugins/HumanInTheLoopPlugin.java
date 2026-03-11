package com.agentengine.engine.plugins;

import com.agentengine.engine.agents.processors.request.HumanInTheLoopRequestProcessor;
import com.agentengine.engine.agents.processors.request.HumanInTheLoopUtils;
import com.agentengine.engine.api.utils.ContentUtils;
import com.agentengine.engine.guardrails.GuardrailUtils;
import com.agentengine.engine.utils.SessionPauseReason;
import com.agentengine.engine.utils.SessionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import io.reactivex.rxjava3.core.Maybe;

/** Handles short-circuit HITL semantics before model invocation. */
public final class HumanInTheLoopPlugin extends BasePlugin {
  private static final String NAME = "human_in_the_loop";

  public HumanInTheLoopPlugin() {
    super(NAME);
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder requestBuilder) {
    final InvocationContext context = callbackContext.invocationContext();

    if (!SessionUtils.isPaused(context)) {
      return Maybe.empty();
    }

    final LlmRequest request = requestBuilder.build();
    final String userAnswer = ContentUtils.extractLatestUserText(request);
    final String pendingConfirmationId = HumanInTheLoopUtils.findPendingConfirmationId(context);
    final SessionPauseReason pauseReason =
        SessionPauseReason.valueOfOrDefault(SessionUtils.getPauseReason(context));
    final boolean isConfirmationPause =
        pauseReason == SessionPauseReason.TOOL_CONFIRMATION
            || StringUtils.isNotBlank(pendingConfirmationId);

    if (isConfirmationPause
        && HumanInTheLoopUtils.isSameInvocationAsPause(
            context, SessionUtils.getPauseInvocationId(context))) {
      context.setEndInvocation(true);
      final String prompt = SessionUtils.getPausePrompt(context);
      final String message =
          StringUtils.isNotBlank(prompt)
              ? prompt
              : "Execution is paused and requires user confirmation.";
      return Maybe.just(GuardrailUtils.buildGuardrailResponse(message));
    }

    if (StringUtils.isBlank(userAnswer)) {
      context.setEndInvocation(true);
      final String message =
          isConfirmationPause
              ? "No confirmation answer was provided. Provide your confirmation text to continue."
              : HumanInTheLoopUtils.buildMissingAnswerMessage(
                  SessionUtils.getPausePrompt(context));
      return Maybe.just(GuardrailUtils.buildGuardrailResponse(message));
    }
    if (isConfirmationPause
        && !HumanInTheLoopRequestProcessor.hasExplicitToolDecisionMarker(userAnswer)) {
      context.setEndInvocation(true);
      return Maybe.just(
          GuardrailUtils.buildGuardrailResponse(
              "Missing explicit tool confirmation decision. Resume with decision APPROVE or REJECT."));
    }
    final LlmRequest updated =
        HumanInTheLoopRequestProcessor.INSTANCE
            .processRequest(context, request)
            .blockingGet()
            .updatedRequest();
    requestBuilder.contents(updated.contents());
    return Maybe.empty();
  }
}
