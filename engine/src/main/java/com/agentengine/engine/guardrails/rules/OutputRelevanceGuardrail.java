package com.agentengine.engine.guardrails.rules;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.engine.api.beans.config.RelevanceMode;
import com.agentengine.util.StringUtils;
import com.agentengine.engine.guardrails.Guardrail;
import com.agentengine.engine.guardrails.GuardrailContext;
import com.agentengine.engine.guardrails.GuardrailDecision;
import com.agentengine.engine.guardrails.GuardrailConstants;
import com.agentengine.engine.guardrails.GuardrailUtils;
import com.agentengine.engine.guardrails.RelevanceAnchorBuilder;
import com.agentengine.engine.guardrails.RelevanceEvaluator;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import java.util.Map;
import java.util.Objects;

public final class OutputRelevanceGuardrail implements Guardrail {
  private final OutputRelevanceGuardrailRule config;
  private final RelevanceEvaluator evaluator;
  private final String id;

  public OutputRelevanceGuardrail(final OutputRelevanceGuardrailRule config, final RelevanceEvaluator evaluator) {
    this.config = Objects.requireNonNull(config);
    this.evaluator = evaluator;
    this.id = GuardrailUtils.resolveRuleId(config, "output_relevance");
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public GuardrailStage stage() {
    return GuardrailStage.OUTPUT;
  }

  @Override
  public GuardrailDecision evaluate(final GuardrailContext context) {
    if (context.invocationContext() == null || StringUtils.isBlank(context.text())) {
      return GuardrailDecision.allow();
    }

    final String anchor = RelevanceAnchorBuilder.build(context.invocationContext(), config);
    final double score = evaluator.score(anchor, context.text());
    final double threshold = config.getRelevanceThreshold();
    final RunState runState = RunStateUtils.getState(context.invocationContext());

    if (score >= threshold) {
      runState.resetOffTopicRetries();
      return GuardrailDecision.allow();
    }

    final int attempt = runState.incrementOffTopicRetries();
    final RelevanceMode mode =
        config.getMode() == null ? RelevanceMode.STEER_THEN_BLOCK : config.getMode();

    if (mode == RelevanceMode.STEER_ONLY) {
      return new GuardrailDecision(
          GuardrailAction.WARN,
          GuardrailConstants.Code.RELEVANCE_STEER,
          "Response drifted from user intent. Regenerate strictly on the active request.",
          Map.of(
              GuardrailConstants.DetailKey.SCORE, score,
              GuardrailConstants.DetailKey.THRESHOLD, threshold,
              GuardrailConstants.DetailKey.ATTEMPT, attempt,
              GuardrailConstants.DetailKey.RETRY_REQUIRED, true));
    }

    if (mode == RelevanceMode.STEER_THEN_ALLOW
        && attempt <= config.getMaxSteeringRetries()) {
      return new GuardrailDecision(
          GuardrailAction.WARN,
          GuardrailConstants.Code.RELEVANCE_STEER,
          "Response is not aligned with the user request. Regenerate with stronger relevance.",
          Map.of(
              GuardrailConstants.DetailKey.SCORE, score,
              GuardrailConstants.DetailKey.THRESHOLD, threshold,
              GuardrailConstants.DetailKey.ATTEMPT, attempt,
              GuardrailConstants.DetailKey.RETRY_REQUIRED, true));
    } else if (mode == RelevanceMode.STEER_THEN_ALLOW) {
      runState.resetOffTopicRetries();
      return GuardrailDecision.allow();
    }

    if (mode == RelevanceMode.STEER_THEN_BLOCK
        && attempt <= config.getMaxSteeringRetries()) {
      return new GuardrailDecision(
          GuardrailAction.WARN,
          GuardrailConstants.Code.RELEVANCE_STEER,
          "Response is not aligned with the user request. Regenerate with stronger relevance.",
          Map.of(
              GuardrailConstants.DetailKey.SCORE, score,
              GuardrailConstants.DetailKey.THRESHOLD, threshold,
              GuardrailConstants.DetailKey.ATTEMPT, attempt,
              GuardrailConstants.DetailKey.RETRY_REQUIRED, true));
    }

    return new GuardrailDecision(
        GuardrailAction.BLOCK,
        GuardrailConstants.Code.RELEVANCE_BLOCK,
        "The response repeatedly drifted from the user request and was blocked.",
        Map.of(
            GuardrailConstants.DetailKey.SCORE, score,
            GuardrailConstants.DetailKey.THRESHOLD, threshold,
            GuardrailConstants.DetailKey.ATTEMPT, attempt));
  }
}
