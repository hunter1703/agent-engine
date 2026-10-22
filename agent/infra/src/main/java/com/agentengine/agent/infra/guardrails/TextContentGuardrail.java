package com.agentengine.agent.infra.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailStage;
import com.agentengine.util.agents.beans.config.TextContentGuardrailRule;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Enforces text content constraints on input and output: maximum length and blocked patterns.
 *
 * <p>Evaluates text in two sequential checks: if a max length applies and is exceeded, the
 * configured action is returned with the actual and the maximum length; else if any blocked pattern
 * (a regex, case-insensitive) matches, the configured action is returned.
 *
 * <p>The max length is the configured one. A rule on the INPUT stage that configures neither a max
 * length nor any pattern limits input to 12,000 characters. Output has no limit unless configured.
 *
 * <p><b>Use cases:</b> protect against excessively long inputs, prevent specific patterns (such as
 * sensitive keywords) in input or output.
 */
public final class TextContentGuardrail implements Guardrail {
  private static final int DEFAULT_MAX_INPUT = 12_000;

  private final TextContentGuardrailRule rule;
  private final List<Pattern> blockedPatterns;
  private final int maxTextLength;

  /**
   * @throws IllegalArgumentException when a blocked pattern is not a valid regex
   */
  public TextContentGuardrail(final TextContentGuardrailRule rule) {
    this.rule = Objects.requireNonNull(rule);
    this.blockedPatterns =
        CollectionUtils.nullSafeList(rule.getBlockedPatterns()).stream()
            .filter(StringUtils::isNotBlank)
            .map(
                pattern ->
                    Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();
    this.maxTextLength =
        rule.getMaxTextLength() != null
            ? rule.getMaxTextLength()
            : stage() == GuardrailStage.INPUT && blockedPatterns.isEmpty() ? DEFAULT_MAX_INPUT : -1;
  }

  @Override
  public String id() {
    return rule.getId();
  }

  @Override
  public GuardrailStage stage() {
    return rule.stageEnum();
  }

  @Override
  public GuardrailDecision evaluate(final GuardrailContext context) {
    final String text = context.text();
    if (maxTextLength > 0 && text.length() > maxTextLength) {
      return violation(
          Kind.LENGTH,
          Map.of(
              GuardrailConstants.DetailKey.RULE,
              rule.getId(),
              GuardrailConstants.DetailKey.ACTUAL_LENGTH,
              text.length(),
              GuardrailConstants.DetailKey.MAX_LENGTH,
              maxTextLength));
    }
    if (blockedPatterns.stream().anyMatch(pattern -> pattern.matcher(text).find())) {
      return violation(Kind.PATTERN, Map.of(GuardrailConstants.DetailKey.RULE, rule.getId()));
    }
    return GuardrailDecision.allow();
  }

  private GuardrailDecision violation(final Kind kind, final Map<String, Object> details) {
    final boolean input = stage() == GuardrailStage.INPUT;
    final String code =
        kind == Kind.LENGTH
            ? input ? GuardrailConstants.Code.INPUT_LENGTH : GuardrailConstants.Code.OUTPUT_LENGTH
            : input ? GuardrailConstants.Code.INPUT_PATTERN : GuardrailConstants.Code.OUTPUT_PATTERN;
    final String message =
        StringUtils.isNotBlank(rule.getMessage())
            ? rule.getMessage()
            : "%s %s."
                .formatted(
                    input ? "Input" : "Output",
                    kind == Kind.LENGTH
                        ? "exceeded the configured max length"
                        : "matched a blocked pattern");
    return GuardrailUtils.fromAction(rule.actionEnum(), code, message, details);
  }

  private enum Kind {
    LENGTH,
    PATTERN
  }
}
