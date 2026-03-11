package com.agentengine.engine.guardrails.rules;

import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.TextContentGuardrailRule;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.guardrails.Guardrail;
import com.agentengine.engine.guardrails.GuardrailConstants;
import com.agentengine.engine.guardrails.GuardrailContext;
import com.agentengine.engine.guardrails.GuardrailDecision;
import com.agentengine.engine.guardrails.GuardrailUtils;
import com.agentengine.util.common.StringUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TextContentGuardrail implements Guardrail {
  private static final int DEFAULT_MAX_INPUT = 12_000;
  private static final List<String> DEFAULT_INPUT_BLOCK_PATTERNS =
      List.of("ignore previous instructions", "reveal system prompt", "jailbreak", "bypass safety");

  private final TextContentGuardrailRule rule;
  private final String id;

  public TextContentGuardrail(final TextContentGuardrailRule rule) {
    this.rule = Objects.requireNonNull(rule);
    this.id = GuardrailUtils.resolveRuleId(rule, "text_content");
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public GuardrailStage stage() {
    return rule.getStage();
  }

  @Override
  public GuardrailDecision evaluate(final GuardrailContext context) {
    final String text = context.text();
    if (StringUtils.isBlank(text)) {
      return GuardrailDecision.allow();
    }

    final GuardrailStage stage = stage();
    final int maxTextLength = resolveMaxTextLength(stage);
    if (maxTextLength > 0 && text.length() > maxTextLength) {
      return GuardrailUtils.fromAction(
          rule.getAction(),
          code(stage, "length"),
          defaultMessage(stage, "length"),
          Map.of(
              GuardrailConstants.DetailKey.RULE, rule.getId(),
              GuardrailConstants.DetailKey.ACTUAL_LENGTH, text.length(),
              GuardrailConstants.DetailKey.MAX_LENGTH, maxTextLength));
    }

    final List<String> blockedPatterns =
        CollectionUtils.isEmpty(rule.getBlockedPatterns()) && stage == GuardrailStage.INPUT
            ? DEFAULT_INPUT_BLOCK_PATTERNS
            : CollectionUtils.nullSafeList(rule.getBlockedPatterns());
    if (GuardrailUtils.containsPattern(text, blockedPatterns)) {
      return GuardrailUtils.fromAction(
          rule.getAction(),
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
    return stage == GuardrailStage.INPUT
        ? "Input matched a blocked pattern."
        : "Output matched a blocked pattern.";
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
