package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GuardrailUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuardrailUtils.class);

   private GuardrailUtils() {}

   public static GuardrailDecision evaluate(final GuardrailContext context, final List<? extends Guardrail> guardrails, final GuardrailErrorMode errorMode) {
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
       }
       return decision;
   }

    private static GuardrailDecision fallbackForError(final GuardrailErrorMode errorMode, final String guardrailId, final Exception ex) {
        final String message =
                "Guardrail '" + guardrailId + "' failed: " + ex.getMessage();
        return errorMode == GuardrailErrorMode.FAIL_OPEN
                ? GuardrailDecision.warn("guardrail_runtime_warn", message)
                : GuardrailDecision.block("guardrail_runtime_block", message);
    }

    public static boolean containsPattern(final String text, final List<String> patterns) {
      if (StringUtils.isBlank(text)) {
        return false;
      }
      final String normalized = text.toLowerCase(Locale.ROOT);
      for (final String pattern : CollectionUtils.nullSafeList(patterns)) {
        if (StringUtils.isBlank(pattern)) {
          continue;
        }
        if (normalized.contains(pattern.toLowerCase(Locale.ROOT))) {
          return true;
        }
      }
      return false;
    }

    public static GuardrailDecision fromAction(final GuardrailAction action, final String code, final String message, final Map<String, Object> details) {
      final GuardrailAction effective = action == null ? GuardrailAction.WARN : action;
      return switch (effective) {
        case ALLOW -> GuardrailDecision.allow();
        case WARN, UNKNOWN -> new GuardrailDecision(GuardrailAction.WARN, code, message, details);
        case BLOCK -> new GuardrailDecision(GuardrailAction.BLOCK, code, message, details);
        case ESCALATE -> new GuardrailDecision(GuardrailAction.ESCALATE, code, message, details);
      };
    }
}
