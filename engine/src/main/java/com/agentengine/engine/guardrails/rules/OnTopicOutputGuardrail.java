package com.agentengine.engine.guardrails.rules;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailStage;
import com.agentengine.engine.api.beans.config.OnTopicMode;
import com.agentengine.engine.api.beans.config.TopicAnchorStrategy;
import com.agentengine.engine.api.beans.config.TopicControlConfig;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.guardrails.Guardrail;
import com.agentengine.engine.guardrails.GuardrailContext;
import com.agentengine.engine.guardrails.GuardrailDecision;
import com.agentengine.engine.guardrails.OnTopicEvaluator;
import com.agentengine.engine.guardrails.TopicAnchorBuilder;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import java.util.Map;

public final class OnTopicOutputGuardrail implements Guardrail {

  @Override
  public String id() {
    return "on_topic_output";
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

    final TopicControlConfig topicControl =
        context.guardrailsConfig() == null ? null : context.guardrailsConfig().getTopicControl();
    if (topicControl == null || !topicControl.isEnabled()) {
      return GuardrailDecision.allow();
    }

    final String anchor = TopicAnchorBuilder.build(context.invocationContext(), topicControl);
    final double score = OnTopicEvaluator.score(anchor, context.text());
    final double threshold = topicControl.getRelevanceThreshold();
    final RunState runState = RunStateUtils.getState(context.invocationContext());

    if (score >= threshold) {
      runState.resetOffTopicRetries();
      return GuardrailDecision.allow();
    }

    final int attempt = runState.incrementOffTopicRetries();
    final OnTopicMode mode =
        topicControl.getMode() == null ? OnTopicMode.STEER_THEN_BLOCK : topicControl.getMode();

    if (mode == OnTopicMode.STEER_ONLY) {
      return new GuardrailDecision(
          GuardrailAction.WARN,
          "off_topic_steer",
          "Response drifted from user intent. Regenerate strictly on the active topic.",
          Map.of("score", score, "threshold", threshold, "attempt", attempt));
    }

    if (mode == OnTopicMode.STEER_THEN_BLOCK && attempt <= topicControl.getMaxSteeringRetries()) {
      return new GuardrailDecision(
          GuardrailAction.WARN,
          "off_topic_steer",
          "Response is off-topic. Regenerate using the latest user request and active task focus.",
          Map.of("score", score, "threshold", threshold, "attempt", attempt));
    }

    return new GuardrailDecision(
        GuardrailAction.BLOCK,
        "off_topic_block",
        "The response repeatedly drifted off-topic and was blocked.",
        Map.of("score", score, "threshold", threshold, "attempt", attempt));
  }
}
