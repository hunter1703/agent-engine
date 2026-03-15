package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailExecutionMode;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.ContentUtils;
import com.agentengine.engine.utils.EventUtils;
import com.agentengine.engine.utils.ResponseUtils;
import com.agentengine.engine.utils.RunUtils;
import com.agentengine.engine.utils.SessionUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized guardrail enforcement plugin for all runtime stages.
 *
 * <p>
 * Policies are compiled once per runtime and selected per agent at callback
 * time.
 */
public final class GuardrailPlugin extends BasePlugin {
  private static final Logger LOG = LoggerFactory.getLogger(GuardrailPlugin.class);
  private static final String NAME = "engine_guardrails";
  private static final String OPTIMISTIC_OUTPUT_FUTURE_PREFIX = "guardrails.optimistic.output.";
  private static final long OPTIMISTIC_FINAL_WAIT_MILLIS = 200L;

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
    final GuardrailDecision decision = GuardrailUtils
        .evaluate(GuardrailContext.builder().invocationContext(invocationContext).text(text).build(), guardrails, policy.errorMode());
    if (policy.executionMode() == GuardrailExecutionMode.OPTIMISTIC) {
      if (decision.action() != GuardrailAction.ALLOW) {
        GuardrailUtils.recordViolation(invocationContext, optimisticFlagDecision(decision));
      }
      return Maybe.empty();
    }
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

    if (policy.executionMode() == GuardrailExecutionMode.OPTIMISTIC) {
      scheduleOptimisticOutputDecision(invocationContext, content.text(), guardrails, policy);
      return Maybe.empty();
    }

    final GuardrailDecision decision = GuardrailUtils.evaluate(
        GuardrailContext.builder().invocationContext(invocationContext).text(content.text()).build(), guardrails, policy.errorMode());
    return handleOutputDecision(invocationContext, llmResponse, content, decision);
  }

  @Override
  public Maybe<Event> onEventCallback(final InvocationContext invocationContext, final Event event) {
    if (ToolUtils.isHumanInTheLoopToolEvent(event)) {
      EventUtils.markAsInternal(event);
    }
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    if (policy.executionMode() != GuardrailExecutionMode.OPTIMISTIC) {
      return Maybe.empty();
    }
    final CompletableFuture<GuardrailDecision> future = getOptimisticFuture(invocationContext);
    if (future == null) {
      return Maybe.empty();
    }
    final boolean terminalEvent = EventUtils.isTerminal(event);
    final GuardrailDecision decision = resolveFutureDecision(future, terminalEvent ? OPTIMISTIC_FINAL_WAIT_MILLIS : 0L);
    if (decision == null) {
      return Maybe.empty();
    }
    clearOptimisticFuture(invocationContext);
    if (decision.action() == GuardrailAction.ALLOW) {
      return Maybe.empty();
    }
    GuardrailUtils.recordViolation(invocationContext, optimisticFlagDecision(decision));
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
      RunUtils.getState(invocationContext).requestContinuation();
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

  private static GuardrailDecision optimisticFlagDecision(final GuardrailDecision decision) {
    final Map<String, Object> details = new LinkedHashMap<>(decision.details());
    details.put("executionMode", GuardrailExecutionMode.OPTIMISTIC.name());
    details.put("async", true);
    return new GuardrailDecision(GuardrailAction.WARN, decision.code(), decision.message(), details);
  }

  private GuardrailPolicyFactory.GuardrailPolicy resolvePolicy(final InvocationContext invocationContext) {
    if (invocationContext == null || invocationContext.agent() == null) {
      return GuardrailPolicyFactory.GuardrailPolicy.disabled();
    }
    final String agentId = invocationContext.agent().name();
    GuardrailPolicyFactory.GuardrailPolicy policy = policyByAgentId.get(agentId);
    return policy != null ? policy : GuardrailPolicyFactory.GuardrailPolicy.disabled();
  }

  private void scheduleOptimisticOutputDecision(final InvocationContext invocationContext, final String text,
      final List<Guardrail> guardrails, final GuardrailPolicyFactory.GuardrailPolicy policy) {
    if (invocationContext == null || StringUtils.isBlank(invocationContext.invocationId())) {
      return;
    }
    final CompletableFuture<GuardrailDecision> future = CompletableFuture.supplyAsync(() -> GuardrailUtils
        .evaluate(GuardrailContext.builder().invocationContext(invocationContext).text(text).build(), guardrails, policy.errorMode()));
    setOptimisticFuture(invocationContext, future);
  }

  private static GuardrailDecision resolveFutureDecision(final CompletableFuture<GuardrailDecision> future, final long waitMillis) {
    if (future == null) {
      return null;
    }
    try {
      if (waitMillis > 0) {
        return future.get(waitMillis, TimeUnit.MILLISECONDS);
      }
      if (!future.isDone()) {
        return null;
      }
      return future.getNow(GuardrailDecision.allow());
    } catch (TimeoutException ignored) {
      future.cancel(true);
      return null;
    } catch (Exception ex) {
      return GuardrailDecision.block(GuardrailConstants.Code.OUTPUT_BLOCK, "Optimistic guardrail evaluation failed.");
    }
  }

  private static void setOptimisticFuture(final InvocationContext invocationContext, final CompletableFuture<GuardrailDecision> future) {
    final ConcurrentMap<String, Object> state = SessionUtils.state(invocationContext);
    if (state == null || future == null) {
      return;
    }
    state.put(optimisticOutputKey(invocationContext), future);
  }

  @SuppressWarnings("unchecked")
  private static CompletableFuture<GuardrailDecision> getOptimisticFuture(final InvocationContext invocationContext) {
    final ConcurrentMap<String, Object> state = SessionUtils.state(invocationContext);
    if (state == null) {
      return null;
    }
    final Object value = state.get(optimisticOutputKey(invocationContext));
    if (value instanceof CompletableFuture<?> future) {
      return (CompletableFuture<GuardrailDecision>) future;
    }
    return null;
  }

  private static void clearOptimisticFuture(final InvocationContext invocationContext) {
    final ConcurrentMap<String, Object> state = SessionUtils.state(invocationContext);
    if (state == null) {
      return;
    }
    state.remove(optimisticOutputKey(invocationContext));
  }

  private static String optimisticOutputKey(final InvocationContext invocationContext) {
    final String invocationId = invocationContext == null || StringUtils.isBlank(invocationContext.invocationId())
        ? ""
        : invocationContext.invocationId();
    return OPTIMISTIC_OUTPUT_FUTURE_PREFIX + invocationId;
  }

  private static Map<String, Object> blockedToolResult() {
    return Map.of(GuardrailConstants.ToolResultKey.MESSAGE, "Tool execution was cancelled by the user.");
  }
}
