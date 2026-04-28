package com.agentengine.agent.infra.guardrails;

import com.agentengine.agent.infra.utils.GuardrailUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.beans.config.GuardrailAction;
import com.agentengine.util.agents.beans.config.GuardrailStage;
import com.agentengine.util.agents.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.util.agents.beans.config.RelevanceMode;
import java.util.Map;
import java.util.Objects;

/**
 * Detects and corrects output drift from the user's original request via semantic scoring.
 *
 * <p><b>Mechanism:</b> Scores the model's output against an anchor prompt (derived from the user's
 * original request and agent context) using semantic similarity. If the score is below the
 * configured threshold, the output is considered off-topic. The guardrail tracks consecutive
 * off-topic attempts per session and applies a configured mode to decide whether to steer (request
 * regeneration), allow anyway, or block.
 *
 * <p><b>Modes (with max steering retries):</b>
 *
 * <ul>
 *   <li><b>STEER_ONLY:</b> Always returns WARN (triggers regeneration indefinitely).
 *   <li><b>STEER_THEN_ALLOW:</b> Returns WARN for up to N retries, then ALLOW (content accepted
 *       regardless of score).
 *   <li><b>STEER_THEN_BLOCK:</b> Returns WARN for up to N retries, then BLOCK (content rejected
 *       after N attempts). Default if mode is UNKNOWN.
 * </ul>
 *
 * <p><b>Retry counter:</b> Tracks consecutive off-topic outputs per session. Resets to zero when
 * output is on-topic (score >= threshold) or when the guardrail allows the output (after max
 * retries in STEER_THEN_ALLOW mode). This prevents infinite steering loops while allowing recovery
 * if the model succeeds later.
 *
 * <p><b>Use case:</b> Prevent the model from drifting into tangential topics, maintaining focus on
 * the user's original intent when multi-turn conversations or complex prompts cause hallucination.
 */
public final class OutputRelevanceGuardrail implements Guardrail {
    private final OutputRelevanceGuardrailRule config;
    private final RelevanceScorer relevanceScorer;

    public OutputRelevanceGuardrail(final OutputRelevanceGuardrailRule config, final RelevanceScorer relevanceScorer) {
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
        if (context.invocationContext() == null) {
            return GuardrailDecision.allow();
        }

        final String anchor = GuardrailUtils.buildRelevanceAnchorPrompt(context.invocationContext(), config);
        final double score = relevanceScorer.score(anchor, context.text());
        final double threshold = config.getRelevanceThreshold();
        final RunState runState = RunUtils.getOrInitState(context.invocationContext());

        if (score >= threshold) {
            runState.resetOffTopicRetries();
            return GuardrailDecision.allow();
        }

        final int attempt = runState.incrementOffTopicRetries();
        final RelevanceMode mode =
                config.modeEnum() == RelevanceMode.UNKNOWN ? RelevanceMode.STEER_THEN_BLOCK : config.modeEnum();

        if (mode == RelevanceMode.STEER_ONLY) {
            return new GuardrailDecision(
                    GuardrailAction.WARN,
                    GuardrailConstants.Code.RELEVANCE_STEER,
                    "Response drifted from user intent. Regenerate strictly on the active request.",
                    Map.of(
                            GuardrailConstants.DetailKey.SCORE,
                            score,
                            GuardrailConstants.DetailKey.THRESHOLD,
                            threshold,
                            GuardrailConstants.DetailKey.ATTEMPT,
                            attempt,
                            GuardrailConstants.DetailKey.RETRY_REQUIRED,
                            true));
        }

        if (mode == RelevanceMode.STEER_THEN_ALLOW && attempt <= config.getMaxSteeringRetries()) {
            return new GuardrailDecision(
                    GuardrailAction.WARN,
                    GuardrailConstants.Code.RELEVANCE_STEER,
                    "Response is not aligned with the user request. Regenerate with stronger relevance.",
                    Map.of(
                            GuardrailConstants.DetailKey.SCORE,
                            score,
                            GuardrailConstants.DetailKey.THRESHOLD,
                            threshold,
                            GuardrailConstants.DetailKey.ATTEMPT,
                            attempt,
                            GuardrailConstants.DetailKey.RETRY_REQUIRED,
                            true));
        } else if (mode == RelevanceMode.STEER_THEN_ALLOW) {
            runState.resetOffTopicRetries();
            return GuardrailDecision.allow();
        }

        if (mode == RelevanceMode.STEER_THEN_BLOCK && attempt <= config.getMaxSteeringRetries()) {
            return new GuardrailDecision(
                    GuardrailAction.WARN,
                    GuardrailConstants.Code.RELEVANCE_STEER,
                    "Response is not aligned with the user request. Regenerate with stronger relevance.",
                    Map.of(
                            GuardrailConstants.DetailKey.SCORE,
                            score,
                            GuardrailConstants.DetailKey.THRESHOLD,
                            threshold,
                            GuardrailConstants.DetailKey.ATTEMPT,
                            attempt,
                            GuardrailConstants.DetailKey.RETRY_REQUIRED,
                            true));
        }

        return new GuardrailDecision(
                GuardrailAction.BLOCK,
                GuardrailConstants.Code.RELEVANCE_BLOCK,
                "The response repeatedly drifted from the user request and was blocked.",
                Map.of(
                        GuardrailConstants.DetailKey.SCORE,
                        score,
                        GuardrailConstants.DetailKey.THRESHOLD,
                        threshold,
                        GuardrailConstants.DetailKey.ATTEMPT,
                        attempt));
    }
}
