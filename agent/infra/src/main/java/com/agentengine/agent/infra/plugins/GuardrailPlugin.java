package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.guardrails.Guardrail;
import com.agentengine.agent.infra.guardrails.GuardrailConstants;
import com.agentengine.agent.infra.guardrails.GuardrailContext;
import com.agentengine.agent.infra.guardrails.GuardrailDecision;
import com.agentengine.agent.infra.guardrails.GuardrailPolicyFactory;
import com.agentengine.agent.infra.guardrails.GuardrailUtils;
import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.agents.ContentUtils;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.agents.beans.config.GuardrailAction;
import com.agentengine.util.agents.beans.config.GuardrailStage;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;

/**
 * Centralized guardrail enforcement plugin for all runtime stages.
 *
 * <p>Policies are compiled once per runtime and selected per agent at callback time.
 */
public final class GuardrailPlugin extends BasePlugin {
  private static final String NAME = "guardrails";

  private final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policyByAgentId;

  public GuardrailPlugin(
      final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policyByAgentId) {
    super(NAME);
    this.policyByAgentId = CollectionUtils.nullSafeMap(policyByAgentId);
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {
    final InvocationContext invocationContext = callbackContext.invocationContext();
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    if (CollectionUtils.isNotEmpty(policy.guardrails(GuardrailStage.OUTPUT))) {
      // The reply is judged once it is complete, so its text is kept from the user until then.
      SessionUtils.getSessionState(invocationContext).runState().holdBackText();
    }
    final List<Guardrail> guardrails = policy.guardrails(GuardrailStage.INPUT);
    // The input is the run's message, so it is judged once however many model calls the run makes.
    if (CollectionUtils.isEmpty(guardrails)
        || !SessionUtils.getSessionState(invocationContext).runState().startInputGuardrails()) {
      return Maybe.empty();
    }
    final String text = ContentUtils.extractLatestUserSentText(llmRequestBuilder.build().contents());
    if (StringUtils.isBlank(text)) {
      return Maybe.empty();
    }
    final GuardrailDecision decision =
        GuardrailUtils.evaluate(
            new GuardrailContext(text, invocationContext, false), guardrails, policy.errorMode());
    return handleInputDecision(callbackContext, decision);
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse llmResponse) {
    final InvocationContext invocationContext = callbackContext.invocationContext();
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    final List<Guardrail> guardrails = policy.guardrails(GuardrailStage.OUTPUT);
    if (llmResponse == null
        || llmResponse.partial().orElse(false)
        || llmResponse.content().isEmpty()
        || CollectionUtils.isEmpty(guardrails)) {
      return Maybe.empty();
    }
    final Content content = llmResponse.content().orElse(null);
    if (!ContentUtils.hasVisibleText(content)) {
      return Maybe.empty();
    }

    final GuardrailDecision decision =
        GuardrailUtils.evaluate(
            new GuardrailContext(
                content.text(), invocationContext, ResponseUtils.isFinalAnswer(llmResponse)),
            guardrails,
            policy.errorMode());
    return handleOutputDecision(callbackContext, llmResponse, content, decision);
  }

  private static Maybe<LlmResponse> handleInputDecision(
      final CallbackContext callbackContext, final GuardrailDecision decision) {
    if (decision.action() == GuardrailAction.ALLOW) {
      return Maybe.empty();
    }
    final InvocationContext invocationContext = callbackContext.invocationContext();
    final Violation violation = GuardrailUtils.buildViolation(decision);
    SessionUtils.getSessionState(invocationContext).runState()
        .addSignal(callbackContext, new Signal<>(violation.id(), violation, false));
    return switch (decision.action()) {
      case ESCALATE ->
          SessionUtils.isResumable(invocationContext)
              ? Maybe.just(ResponseUtils.requestHumanToDecide(decision.message()))
              : Maybe.just(GuardrailUtils.buildGuardrailResponse(decision.message()));
      case BLOCK -> Maybe.just(GuardrailUtils.buildGuardrailResponse(decision.message()));
      default -> Maybe.empty();
    };
  }

  private static Maybe<LlmResponse> handleOutputDecision(
      final CallbackContext callbackContext,
      final LlmResponse response,
      final Content content,
      final GuardrailDecision decision) {
    if (decision.action() == GuardrailAction.ALLOW) {
      return Maybe.empty();
    }
    final InvocationContext invocationContext = callbackContext.invocationContext();
    final Violation violation = GuardrailUtils.buildViolation(decision);
    final boolean warn = decision.action() == GuardrailAction.WARN;
    SessionUtils.getSessionState(invocationContext).runState()
        .addSignal(
            callbackContext,
            new Signal<>(violation.id(), violation, warn && requiresRegeneration(decision)));
    if (warn) {
      return Maybe.empty();
    }
    if (decision.action() == GuardrailAction.ESCALATE
        && SessionUtils.isResumable(invocationContext)) {
      return Maybe.just(ResponseUtils.requestHumanToDecide(decision.message()));
    }
    final String blockMessage =
        StringUtils.isNotBlank(decision.message())
            ? decision.message()
            : "The response was blocked by guardrail policy.";
    final Content.Builder builder = Content.builder().parts(List.of(Part.fromText(blockMessage)));
    content.role().ifPresent(builder::role);
    return Maybe.just(response.toBuilder().content(builder.build()).build());
  }

  private static boolean requiresRegeneration(final GuardrailDecision decision) {
    final Map<String, Object> details = decision.details();
    if (Boolean.TRUE.equals(
        CollectionUtils.getBooleanValueFromMap(
            details, GuardrailConstants.DetailKey.RETRY_REQUIRED))) {
      return true;
    }
    return GuardrailConstants.Code.RELEVANCE_STEER.equals(decision.code());
  }

  private GuardrailPolicyFactory.GuardrailPolicy resolvePolicy(
      final InvocationContext invocationContext) {
    if (invocationContext == null || invocationContext.agent() == null) {
      return GuardrailPolicyFactory.GuardrailPolicy.disabled();
    }
    final String agentId = invocationContext.agent().name();
    GuardrailPolicyFactory.GuardrailPolicy policy = policyByAgentId.get(agentId);
    return policy != null ? policy : GuardrailPolicyFactory.GuardrailPolicy.disabled();
  }
}
