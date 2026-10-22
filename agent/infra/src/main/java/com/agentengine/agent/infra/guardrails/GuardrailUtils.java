package com.agentengine.agent.infra.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailAction;
import com.agentengine.util.agents.beans.config.GuardrailErrorMode;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuardrailUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(GuardrailUtils.class);

  private GuardrailUtils() {}

  /**
   * The decision of the most severe guardrail, asking them in order and stopping at the first that
   * blocks or escalates, since nothing a later one says changes that outcome.
   */
  public static GuardrailDecision evaluate(
      final GuardrailContext context,
      final List<? extends Guardrail> guardrails,
      final GuardrailErrorMode errorMode) {
    GuardrailDecision decision = GuardrailDecision.allow();
    for (final Guardrail guardrail : CollectionUtils.nullSafeList(guardrails)) {
      GuardrailDecision next;
      try {
        next = guardrail.evaluate(context);
      } catch (Exception ex) {
        LOGGER.warn("Guardrail '{}' failed", guardrail.id(), ex);
        next = fallbackForError(errorMode, guardrail.id(), ex);
      }
      decision = GuardrailDecision.merge(decision, next);
      if (decision.isBlocking()) {
        break;
      }
    }
    return decision;
  }

  public static Violation buildViolation(final GuardrailDecision decision) {
    return Violation.builder(
            StringUtils.isBlank(decision.code())
                ? GuardrailConstants.Code.VIOLATION
                : decision.code())
        .message(
            StringUtils.isBlank(decision.message())
                ? "Guardrail policy was triggered."
                : decision.message())
        .details(decision.details())
        .build();
  }

  public static LlmResponse buildGuardrailResponse(final String message) {
    final String text =
        StringUtils.isBlank(message)
            ? "The request was blocked by guardrail policy."
            : message.trim();
    final Content content =
        Content.builder().role("model").parts(List.of(Part.fromText(text))).build();
    return LlmResponse.builder().content(content).build();
  }

  public static GuardrailDecision fromAction(
      final GuardrailAction action,
      final String code,
      final String message,
      final Map<String, Object> details) {
    final GuardrailAction effective = action == null ? GuardrailAction.WARN : action;
    return switch (effective) {
      case ALLOW -> GuardrailDecision.allow();
      case WARN, UNKNOWN -> new GuardrailDecision(GuardrailAction.WARN, code, message, details);
      case BLOCK -> new GuardrailDecision(GuardrailAction.BLOCK, code, message, details);
      case ESCALATE -> new GuardrailDecision(GuardrailAction.ESCALATE, code, message, details);
    };
  }

  private static GuardrailDecision fallbackForError(
      final GuardrailErrorMode errorMode, final String guardrailId, final Exception ex) {
    final String message = "Guardrail '" + guardrailId + "' failed: " + ex.getMessage();
    return errorMode == GuardrailErrorMode.FAIL_OPEN
        ? GuardrailDecision.warn(GuardrailConstants.Code.RUNTIME_WARN, message)
        : GuardrailDecision.block(GuardrailConstants.Code.RUNTIME_BLOCK, message);
  }
}
