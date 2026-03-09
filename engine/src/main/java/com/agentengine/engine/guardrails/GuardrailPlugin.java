package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailExecutionMode;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.utils.SessionPauseReason;
import com.agentengine.engine.utils.SessionStateUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Centralized guardrail enforcement plugin for all runtime stages.
 *
 * <p>Policies are compiled once per runtime and selected per agent at callback time.
 */
public final class GuardrailPlugin extends BasePlugin {
  private static final String NAME = "engine_guardrails";
  private static final String OPTIMISTIC_OUTPUT_FUTURE_PREFIX = "guardrails.optimistic.output.";
  private static final long OPTIMISTIC_FINAL_WAIT_MILLIS = 200L;

  private final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policyByAgentId;

  public GuardrailPlugin(final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policyByAgentId) {
    super(NAME);
    this.policyByAgentId = CollectionUtils.nullSafeMap(policyByAgentId);
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {
    final InvocationContext invocationContext =
        callbackContext == null ? null : callbackContext.invocationContext();
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    final List<Guardrail> guardrails = policy.guardrails(GuardrailStage.INPUT);
    if (CollectionUtils.isEmpty(guardrails)) {
      return Maybe.empty();
    }
    final String text = GuardrailUtils.extractLatestUserText(llmRequestBuilder.build().contents());
    if (StringUtils.isBlank(text)) {
      return Maybe.empty();
    }
    final GuardrailDecision decision =
        GuardrailUtils.evaluate(
            GuardrailContext.builder().invocationContext(invocationContext).text(text).build(),
            guardrails,
            policy.errorMode());
    return handleInputDecision(invocationContext, decision);
  }

  @Override
  public Maybe<Map<String, Object>> beforeToolCallback(
      final BaseTool tool, final Map<String, Object> toolArgs, final ToolContext toolContext) {
    final InvocationContext invocationContext =
        toolContext == null ? null : toolContext.invocationContext();
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    final List<Guardrail> guardrails = policy.guardrails(GuardrailStage.TOOL);
    if (CollectionUtils.isEmpty(guardrails) || tool == null) {
      return Maybe.empty();
    }
    final GuardrailDecision decision =
            GuardrailUtils.evaluateTool(
                    invocationContext,
                    GuardrailUtils.resolveToolDescriptor(tool),
                    toolArgs,
                    guardrails,
                    policy.errorMode());
    if (decision.action() == GuardrailAction.ALLOW || decision.action() == GuardrailAction.WARN) {
      if (decision.action() == GuardrailAction.WARN) {
        GuardrailUtils.recordViolation(invocationContext, decision);
      }
      return Maybe.empty();
    }
    GuardrailUtils.recordViolation(invocationContext, decision);
    if (decision.action() == GuardrailAction.ESCALATE) {
      if (toolContext == null || toolContext.functionCallId().isEmpty()) {
        return Maybe.empty();
      }
      final String prompt =
              StringUtils.isBlank(decision.message())
                      ? "Tool execution requires confirmation."
                      : decision.message();
      toolContext.requestConfirmation(
              prompt,
              Map.of(
                      GuardrailConstants.ToolResultKey.GUARDRAIL_CODE,
                      StringUtils.isBlank(decision.code())
                              ? GuardrailConstants.Code.TOOL_ESCALATE
                              : decision.code()));
      SessionStateUtils.pause(invocationContext, prompt, SessionPauseReason.TOOL_CONFIRMATION.code());
      return Maybe.just(
              Map.of(
                      GuardrailConstants.ToolResultKey.STATUS, GuardrailConstants.ToolResultStatus.CONFIRMATION_REQUESTED,
                      GuardrailConstants.ToolResultKey.MESSAGE, prompt,
                      GuardrailConstants.ToolResultKey.GUARDRAIL_CODE,
                      StringUtils.isBlank(decision.code())
                              ? GuardrailConstants.Code.TOOL_ESCALATE
                              : decision.code()));
    }
    return Maybe.just(
            Map.of(
                    GuardrailConstants.ToolResultKey.STATUS, GuardrailConstants.ToolResultStatus.BLOCKED,
                    GuardrailConstants.ToolResultKey.ERROR,
                    StringUtils.isBlank(decision.message())
                            ? "Tool execution blocked by policy."
                            : decision.message(),
                    GuardrailConstants.ToolResultKey.GUARDRAIL_CODE,
                    StringUtils.isBlank(decision.code())
                            ? GuardrailConstants.Code.TOOL_BLOCK
                            : decision.code()));
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse llmResponse) {
    final InvocationContext invocationContext =
        callbackContext == null ? null : callbackContext.invocationContext();
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    final List<Guardrail> guardrails = policy.guardrails(GuardrailStage.OUTPUT);
    if (llmResponse == null
        || llmResponse.partial().orElse(false)
        || llmResponse.content().isEmpty()
        || CollectionUtils.isEmpty(guardrails)) {
      return Maybe.empty();
    }
    final Content content = llmResponse.content().orElse(null);
    if (content == null || StringUtils.isBlank(content.text())) {
      return Maybe.empty();
    }

    if (effectiveExecutionMode(policy) == GuardrailExecutionMode.OPTIMISTIC) {
      scheduleOptimisticOutputDecision(invocationContext, content.text(), guardrails, policy);
      return Maybe.empty();
    }

    final GuardrailDecision decision =
        GuardrailUtils.evaluate(
            GuardrailContext.builder().invocationContext(invocationContext).text(content.text()).build(),
            guardrails,
            policy.errorMode());
    return handleOutputDecision(invocationContext, llmResponse, content, decision);
  }

  @Override
  public Maybe<Event> onEventCallback(final InvocationContext invocationContext, final Event event) {
    final GuardrailPolicyFactory.GuardrailPolicy policy = resolvePolicy(invocationContext);
    if (effectiveExecutionMode(policy) != GuardrailExecutionMode.OPTIMISTIC) {
      return Maybe.empty();
    }
    final CompletableFuture<GuardrailDecision> future = getOptimisticFuture(invocationContext);
    if (future == null) {
      return Maybe.empty();
    }
    final boolean terminalEvent =
        event != null && (event.finalResponse() || event.turnComplete().orElse(false));
    final GuardrailDecision decision =
        resolveFutureDecision(future, terminalEvent ? OPTIMISTIC_FINAL_WAIT_MILLIS : 0L);
    if (decision == null) {
      return Maybe.empty();
    }
    clearOptimisticFuture(invocationContext);
    if (decision.action() == GuardrailAction.ALLOW) {
      return Maybe.empty();
    }
    GuardrailUtils.recordViolation(invocationContext, decision);
    if (decision.action() == GuardrailAction.WARN) {
      return Maybe.empty();
    }

    if (invocationContext != null) {
      invocationContext.setEndInvocation(true);
    }
    final String blockMessage =
        StringUtils.isNotBlank(decision.message())
            ? decision.message()
            : "The response was blocked by guardrail policy.";
    final EventActions actions =
        event.actions()
            .toBuilder()
            .endInvocation(true)
            .escalate(decision.action() == GuardrailAction.ESCALATE)
            .build();
    final Content content =
        Content.builder().role("model").parts(List.of(Part.fromText(blockMessage))).build();
    return Maybe.just(event.toBuilder().content(Optional.of(content)).actions(actions).build());
  }

  private static Maybe<LlmResponse> handleInputDecision(
      final InvocationContext invocationContext, final GuardrailDecision decision) {
    if (decision.action() == GuardrailAction.ALLOW || decision.action() == GuardrailAction.WARN) {
      if (decision.action() == GuardrailAction.WARN) {
        GuardrailUtils.recordViolation(invocationContext, decision);
      }
      return Maybe.empty();
    }
    GuardrailUtils.recordViolation(invocationContext, decision);
    if (decision.action() == GuardrailAction.ESCALATE) {
      SessionStateUtils.pause(invocationContext, decision.message(), decision.code());
    }
    return Maybe.just(GuardrailUtils.buildGuardrailResponse(decision.message()));
  }

  private static Maybe<LlmResponse> handleOutputDecision(
      final InvocationContext invocationContext,
      final LlmResponse response,
      final Content content,
      final GuardrailDecision decision) {
    if (decision.action() == GuardrailAction.ALLOW) {
      return Maybe.empty();
    }
    GuardrailUtils.recordViolation(invocationContext, decision);
    if (decision.action() == GuardrailAction.WARN) {
      if (!requiresRegeneration(decision)) {
        return Maybe.empty();
      }
      return Maybe.just(
          response.toBuilder()
              .content(stripPlainText(content))
              .turnComplete(false)
              .finishReason(Optional.empty())
              .build());
    }

    if (decision.action() == GuardrailAction.ESCALATE) {
      SessionStateUtils.pause(invocationContext, decision.message(), decision.code());
    }
    final String blockMessage =
        StringUtils.isNotBlank(decision.message())
            ? decision.message()
            : "The response was blocked by guardrail policy.";
    final Content.Builder builder = Content.builder().parts(List.of(Part.fromText(blockMessage)));
    content.role().ifPresent(builder::role);
    return Maybe.just(response.toBuilder().content(builder.build()).turnComplete(true).build());
  }

  private static Optional<Content> stripPlainText(final Content content) {
    final List<Part> kept =
        CollectionUtils.nullSafeList(content.parts().orElse(List.of())).stream()
            .filter(part -> part.text().isEmpty() || part.thought().orElse(false))
            .toList();
    if (CollectionUtils.isEmpty(kept)) {
      return Optional.empty();
    }
    final Content.Builder builder = Content.builder().parts(kept);
    content.role().ifPresent(builder::role);
    return Optional.of(builder.build());
  }

  private static boolean requiresRegeneration(final GuardrailDecision decision) {
    final Map<String, Object> details = decision == null ? Map.of() : decision.details();
    final Object explicit =
        details == null ? null : details.get(GuardrailConstants.DetailKey.RETRY_REQUIRED);
    if (explicit instanceof Boolean value) {
      return value;
    }
    return decision != null && GuardrailConstants.Code.RELEVANCE_STEER.equals(decision.code());
  }

  private static GuardrailExecutionMode effectiveExecutionMode(
      final GuardrailPolicyFactory.GuardrailPolicy policy) {
    final GuardrailExecutionMode mode =
        policy == null ? GuardrailExecutionMode.SYNC : policy.executionMode();
    return mode == null || mode == GuardrailExecutionMode.UNKNOWN
        ? GuardrailExecutionMode.SYNC
        : mode;
  }

  private GuardrailPolicyFactory.GuardrailPolicy resolvePolicy(final InvocationContext invocationContext) {
    if (invocationContext == null || invocationContext.agent() == null) {
      return GuardrailPolicyFactory.GuardrailPolicy.disabled();
    }
    final String agentId = invocationContext.agent().name();
    return policyByAgentId.getOrDefault(agentId, GuardrailPolicyFactory.GuardrailPolicy.disabled());
  }

  private void scheduleOptimisticOutputDecision(
      final InvocationContext invocationContext,
      final String text,
      final List<Guardrail> guardrails,
      final GuardrailPolicyFactory.GuardrailPolicy policy) {
    if (invocationContext == null || StringUtils.isBlank(invocationContext.invocationId())) {
      return;
    }
    final CompletableFuture<GuardrailDecision> future =
        CompletableFuture.supplyAsync(
            () ->
                GuardrailUtils.evaluate(
                    GuardrailContext.builder()
                        .invocationContext(invocationContext)
                        .text(text)
                        .build(),
                    guardrails,
                    policy.errorMode()));
    setOptimisticFuture(invocationContext, future);
  }

  private static GuardrailDecision resolveFutureDecision(
      final CompletableFuture<GuardrailDecision> future, final long waitMillis) {
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
      return null;
    } catch (Exception ex) {
      return GuardrailDecision.block(
          GuardrailConstants.Code.OUTPUT_BLOCK, "Optimistic guardrail evaluation failed.");
    }
  }

  private static void setOptimisticFuture(
      final InvocationContext invocationContext, final CompletableFuture<GuardrailDecision> future) {
    final ConcurrentMap<String, Object> state = state(invocationContext);
    if (state == null || future == null) {
      return;
    }
    state.put(optimisticOutputKey(invocationContext), future);
  }

  @SuppressWarnings("unchecked")
  private static CompletableFuture<GuardrailDecision> getOptimisticFuture(
      final InvocationContext invocationContext) {
    final ConcurrentMap<String, Object> state = state(invocationContext);
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
    final ConcurrentMap<String, Object> state = state(invocationContext);
    if (state == null) {
      return;
    }
    state.remove(optimisticOutputKey(invocationContext));
  }

  private static String optimisticOutputKey(final InvocationContext invocationContext) {
    final String invocationId =
        invocationContext == null || StringUtils.isBlank(invocationContext.invocationId())
            ? ""
            : invocationContext.invocationId();
    return OPTIMISTIC_OUTPUT_FUTURE_PREFIX + invocationId;
  }

  private static ConcurrentMap<String, Object> state(final InvocationContext invocationContext) {
    if (invocationContext == null
        || invocationContext.session() == null
        || invocationContext.session().state() == null) {
      return null;
    }
    return invocationContext.session().state();
  }
}
