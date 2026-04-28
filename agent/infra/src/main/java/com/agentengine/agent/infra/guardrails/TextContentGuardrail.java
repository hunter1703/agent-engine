package com.agentengine.agent.infra.guardrails;

import com.agentengine.agent.infra.utils.GuardrailUtils;
import com.agentengine.util.agents.beans.config.GuardrailStage;
import com.agentengine.util.agents.beans.config.TextContentGuardrailRule;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Enforces text content constraints on input and output: maximum length and blocked patterns.
 *
 * <p><b>Behavior:</b> Evaluates text in two sequential checks:
 *
 * <ol>
 *   <li>If a max length is configured and exceeded, the configured action (BLOCK/WARN) is returned
 *       with details on actual vs. max length.
 *   <li>If any configured blocked pattern (regex) matches the text, the configured action is
 *       returned.
 * </ol>
 *
 * <p><b>Defaults:</b> INPUT stage has a 12,000-character default limit (unless explicitly
 * configured). OUTPUT stage has no length limit by default. INPUT stage ignores custom blocked
 * patterns if none are configured (using an empty default). OUTPUT stage respects custom patterns.
 *
 * <p><b>Use cases:</b> Protect against excessively long inputs (DoS), prevent injection of specific
 * patterns (e.g., code snippets, sensitive keywords), and enforce output format constraints.
 */
public final class TextContentGuardrail implements Guardrail {
    private static final int DEFAULT_MAX_INPUT = 12_000;
    private static final List<String> DEFAULT_INPUT_BLOCK_PATTERNS = List.of();

    private final TextContentGuardrailRule rule;
    private final String id;

    public TextContentGuardrail(final TextContentGuardrailRule rule) {
        this.rule = Objects.requireNonNull(rule);
        this.id = rule.getId();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public GuardrailStage stage() {
        return rule.stageEnum();
    }

    @Override
    public GuardrailDecision evaluate(final GuardrailContext context) {
        final String text = context.text();

        final GuardrailStage stage = stage();
        final int maxTextLength = resolveMaxTextLength(stage);
        if (maxTextLength > 0 && text.length() > maxTextLength) {
            return GuardrailUtils.fromAction(
                    rule.actionEnum(),
                    code(stage, "length"),
                    defaultMessage(stage, "length"),
                    Map.of(
                            GuardrailConstants.DetailKey.RULE,
                            rule.getId(),
                            GuardrailConstants.DetailKey.ACTUAL_LENGTH,
                            text.length(),
                            GuardrailConstants.DetailKey.MAX_LENGTH,
                            maxTextLength));
        }

        final List<String> blockedPatterns =
                CollectionUtils.isEmpty(rule.getBlockedPatterns()) && stage == GuardrailStage.INPUT
                        ? DEFAULT_INPUT_BLOCK_PATTERNS
                        : CollectionUtils.nullSafeList(rule.getBlockedPatterns());
        if (GuardrailUtils.containsPattern(text, blockedPatterns)) {
            return GuardrailUtils.fromAction(
                    rule.actionEnum(),
                    code(stage, "pattern"),
                    defaultMessage(stage, "pattern"),
                    Map.of(GuardrailConstants.DetailKey.RULE, rule.getId()));
        }

        return GuardrailDecision.allow();
    }

    private int resolveMaxTextLength(final GuardrailStage stage) {
        if (rule.getMaxTextLength() != null) {
            return rule.getMaxTextLength();
        }
        if (stage == GuardrailStage.INPUT) {
            return DEFAULT_MAX_INPUT;
        }
        return -1;
    }

    private String defaultMessage(final GuardrailStage stage, final String kind) {
        if (StringUtils.isNotBlank(rule.getMessage())) {
            return rule.getMessage();
        }
        if ("length".equals(kind)) {
            return stage == GuardrailStage.INPUT
                    ? "Input exceeded the configured max length."
                    : "Output exceeded the configured max length.";
        }
        return stage == GuardrailStage.INPUT ? "Input matched a blocked pattern." : "Output matched a blocked pattern.";
    }

    private static String code(final GuardrailStage stage, final String suffix) {
        if ("length".equals(suffix)) {
            return stage == GuardrailStage.INPUT
                    ? GuardrailConstants.Code.INPUT_LENGTH
                    : GuardrailConstants.Code.OUTPUT_LENGTH;
        }
        return stage == GuardrailStage.INPUT
                ? GuardrailConstants.Code.INPUT_PATTERN
                : GuardrailConstants.Code.OUTPUT_PATTERN;
    }
}
