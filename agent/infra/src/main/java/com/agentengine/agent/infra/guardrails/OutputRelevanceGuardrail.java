package com.agentengine.agent.infra.guardrails;

import static com.agentengine.util.agents.beans.config.RelevanceAnchorStrategy.LATEST_USER_AND_PLAN;
import static com.agentengine.util.agents.beans.config.RelevanceAnchorStrategy.RECENT_USER;
import static com.agentengine.util.agents.beans.config.RelevanceAnchorStrategy.UNKNOWN;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.beans.config.GuardrailAction;
import com.agentengine.util.agents.beans.config.GuardrailStage;
import com.agentengine.util.agents.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.util.agents.beans.config.RelevanceAnchorStrategy;
import com.agentengine.util.agents.beans.config.RelevanceMode;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.InvocationContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detects and corrects drift of the model's final answer from what the user asked, by scoring it
 * against an anchor built from the user's request (and the active plan) with a model.
 *
 * <p>An answer scoring below the configured threshold is off topic. The run counts consecutive
 * off-topic answers, and the configured mode decides what follows:
 *
 * <ul>
 *   <li><b>STEER_ONLY:</b> always asks for a new answer.
 *   <li><b>STEER_THEN_ALLOW:</b> asks for a new answer up to the configured number of times, then
 *       accepts the answer whatever its score.
 *   <li><b>STEER_THEN_BLOCK:</b> asks for a new answer up to the configured number of times, then
 *       blocks. This is the mode when none is configured.
 * </ul>
 *
 * <p>The count resets when an answer is on topic or is accepted after the retries. Only final
 * answers are scored: text written next to a tool call is narration, not the answer.
 */
public final class OutputRelevanceGuardrail implements Guardrail {
  private final OutputRelevanceGuardrailRule config;
  private final RelevanceScorer relevanceScorer;

  public OutputRelevanceGuardrail(
      final OutputRelevanceGuardrailRule config, final RelevanceScorer relevanceScorer) {
    this.config = Objects.requireNonNull(config);
    this.relevanceScorer = relevanceScorer;
  }

  @Override
  public String id() {
    return config.getId();
  }

  @Override
  public GuardrailStage stage() {
    return GuardrailStage.OUTPUT;
  }

  @Override
  public GuardrailDecision evaluate(final GuardrailContext context) {
    if (!context.finalAnswer() || context.invocationContext() == null) {
      return GuardrailDecision.allow();
    }

    final double score =
        relevanceScorer.score(anchorPrompt(context.invocationContext()), context.text());
    final double threshold = config.getRelevanceThreshold();
    final RunState runState = SessionUtils.getSessionState(context.invocationContext()).runState();

    if (score >= threshold) {
      runState.resetOffTopicRetries();
      return GuardrailDecision.allow();
    }

    final int attempt = runState.incrementOffTopicRetries();
    final RelevanceMode mode =
        config.modeEnum() == RelevanceMode.UNKNOWN
            ? RelevanceMode.STEER_THEN_BLOCK
            : config.modeEnum();

    if (mode == RelevanceMode.STEER_ONLY || attempt <= config.getMaxSteeringRetries()) {
      return decision(
          GuardrailAction.WARN,
          GuardrailConstants.Code.RELEVANCE_STEER,
          "Your response is not aligned with the user's request. Answer again, strictly on the request.",
          score,
          threshold,
          attempt,
          true);
    }
    if (mode == RelevanceMode.STEER_THEN_ALLOW) {
      runState.resetOffTopicRetries();
      return GuardrailDecision.allow();
    }
    return decision(
        GuardrailAction.BLOCK,
        GuardrailConstants.Code.RELEVANCE_BLOCK,
        "The response repeatedly drifted from the user request and was blocked.",
        score,
        threshold,
        attempt,
        false);
  }

  private static GuardrailDecision decision(
      final GuardrailAction action,
      final String code,
      final String message,
      final double score,
      final double threshold,
      final int attempt,
      final boolean retryRequired) {
    return new GuardrailDecision(
        action,
        code,
        message,
        Map.of(
            GuardrailConstants.DetailKey.SCORE,
            score,
            GuardrailConstants.DetailKey.THRESHOLD,
            threshold,
            GuardrailConstants.DetailKey.ATTEMPT,
            attempt,
            GuardrailConstants.DetailKey.RETRY_REQUIRED,
            retryRequired));
  }

  private String anchorPrompt(final InvocationContext context) {
    RelevanceAnchorStrategy strategy = config.anchorStrategyEnum();
    if (strategy == UNKNOWN) {
      strategy = LATEST_USER_AND_PLAN;
    }
    final int recency = strategy == RECENT_USER ? Math.max(1, config.getRecency()) : 1;
    final String recentUserMessages = EventUtils.recentUser(context.session().events(), recency);
    if (strategy == RECENT_USER) {
      return recentUserMessages;
    }
    final Plan plan = SessionUtils.getSessionState(context).plan();
    final String planAnchor =
        plan == null
            ? ""
            : StringUtils.joinNonBlank(
                List.of(
                    PlanningUtils.buildPlanSummary(plan), PlanningUtils.buildTaskFocusPrompt(plan)));
    return StringUtils.joinNonBlank(List.of(recentUserMessages, planAnchor));
  }
}
