package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.ContentUtils;
import com.agentengine.engine.utils.EventUtils;
import com.agentengine.engine.utils.ResponseUtils;
import com.agentengine.engine.utils.RunUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
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
 * <p>
 * Policies are compiled once per runtime and selected per agent at callback
 * time.
 */
public final class GuardrailPlugin extends BasePlugin {
  private static final String NAME = "engine_guardrails";

  private final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policyByAgentId;

  public GuardrailPlugin(final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policyByAgentId) {
    super(NAME);
    this.policyByAgentId = CollectionUtils.nullSafeMap(policyByAgentId);
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {
    final InvocationContext invocationContext = callbackContext.invocationContext();
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    final List<Guardrail> guardrails = policy.guardrails(GuardrailStage.INPUT);
    if (CollectionUtils.isEmpty(guardrails)) {
      return Maybe.empty();
    }
    final String text = ContentUtils.extractLatestUserText(llmRequestBuilder.build().contents());
    if (StringUtils.isBlank(text)) {
      return Maybe.empty();
    }
    final GuardrailDecision decision = GuardrailUtils.evaluate(new GuardrailContext(text, invocationContext), guardrails,
        policy.errorMode());
    return handleInputDecision(invocationContext, decision);
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(final CallbackContext callbackContext, final LlmResponse llmResponse) {
    final InvocationContext invocationContext = callbackContext.invocationContext();
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    final List<Guardrail> guardrails = policy.guardrails(GuardrailStage.OUTPUT);
    if (llmResponse == null || llmResponse.partial().orElse(false) || llmResponse.content().isEmpty()
        || CollectionUtils.isEmpty(guardrails)) {
      return Maybe.empty();
    }
    final Content content = llmResponse.content().orElse(null);
    if (!ContentUtils.hasVisibleText(content)) {
      return Maybe.empty();
    }

    final GuardrailDecision decision = GuardrailUtils.evaluate(new GuardrailContext(content.text(), invocationContext), guardrails,
        policy.errorMode());
    return handleOutputDecision(invocationContext, llmResponse, content, decision);
  }

  @Override
  public Maybe<Event> onEventCallback(final InvocationContext invocationContext, final Event event) {
    if (ToolUtils.isHumanInTheLoopToolEvent(event)) {
      EventUtils.markAsInternal(event);
    }
    return Maybe.empty();
  }

  private static Maybe<LlmResponse> handleInputDecision(final InvocationContext invocationContext, final GuardrailDecision decision) {
    if (decision.action() == GuardrailAction.ALLOW) {
      return Maybe.empty();
    }
    if (decision.action() == GuardrailAction.WARN) {
      GuardrailUtils.recordViolation(invocationContext, decision);
      return Maybe.empty();
    }

    GuardrailUtils.recordViolation(invocationContext, decision);
    if (decision.action() == GuardrailAction.ESCALATE) {
      return Maybe.just(ResponseUtils.requestHumanToDecide(decision.message()));
    }
    return Maybe.just(GuardrailUtils.buildGuardrailResponse(decision.message()));
  }

  private static Maybe<LlmResponse> handleOutputDecision(final InvocationContext invocationContext, final LlmResponse response,
      final Content content, final GuardrailDecision decision) {
    if (decision.action() == GuardrailAction.ALLOW) {
      return Maybe.empty();
    }
    GuardrailUtils.recordViolation(invocationContext, decision);
    if (decision.action() == GuardrailAction.WARN) {
      if (!requiresRegeneration(decision)) {
        return Maybe.empty();
      }
      RunUtils.getOrInitState(invocationContext).requestContinuation();
      return Maybe.empty();
    }

    if (decision.action() == GuardrailAction.ESCALATE) {
      return Maybe.just(ResponseUtils.requestHumanToDecide(decision.message()));
    }
    final String blockMessage = StringUtils.isNotBlank(decision.message())
        ? decision.message()
        : "The response was blocked by guardrail policy.";
    final Content.Builder builder = Content.builder().parts(List.of(Part.fromText(blockMessage)));
    content.role().ifPresent(builder::role);
    return Maybe.just(response.toBuilder().content(builder.build()).build());
  }

  private static boolean requiresRegeneration(final GuardrailDecision decision) {
    final Map<String, Object> details = decision.details();
    if (Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(details, GuardrailConstants.DetailKey.RETRY_REQUIRED))) {
      return true;
    }
    return GuardrailConstants.Code.RELEVANCE_STEER.equals(decision.code());
  }

  private GuardrailPolicyFactory.GuardrailPolicy resolvePolicy(final InvocationContext invocationContext) {
    if (invocationContext == null || invocationContext.agent() == null) {
      return GuardrailPolicyFactory.GuardrailPolicy.disabled();
    }
    final String agentId = invocationContext.agent().name();
    GuardrailPolicyFactory.GuardrailPolicy policy = policyByAgentId.get(agentId);
    return policy != null ? policy : GuardrailPolicyFactory.GuardrailPolicy.disabled();
  }
}
