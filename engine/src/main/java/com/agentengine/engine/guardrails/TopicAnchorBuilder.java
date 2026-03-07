package com.agentengine.engine.guardrails;

import com.agentengine.engine.api.beans.config.TopicAnchorStrategy;
import com.agentengine.engine.api.beans.config.OutputRelevanceGuardrailRule;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import java.util.ArrayList;
import java.util.List;

import static com.agentengine.engine.api.beans.config.TopicAnchorStrategy.*;

public final class TopicAnchorBuilder {
  private TopicAnchorBuilder() {}

  public static String build(final InvocationContext context, final TopicAnchorStrategy strategy) {
    final OutputRelevanceGuardrailRule config = new OutputRelevanceGuardrailRule();
    config.setAnchorStrategy(strategy);
    return build(context, config);
  }

  public static String build(
      final InvocationContext context, final OutputRelevanceGuardrailRule config) {
    if (context == null || context.session() == null) {
      return "";
    }
    TopicAnchorStrategy strategy = config.getAnchorStrategy();
    strategy = strategy == null ? LATEST_USER_AND_PLAN : strategy;

    final String latestUser = latestUserMessage(context.session().events());
    final String recentUser = recentUserIntents(context.session().events(), strategy == RECENT_USER ? config.getRecency() : 1);
    final String planAnchor = planAnchor(context);

    return switch (strategy) {
      case LATEST_USER -> latestUser;
      case RECENT_USER -> recentUser;
      case LATEST_USER_AND_PLAN -> joinNonBlank(List.of(latestUser, planAnchor));
      case UNKNOWN -> joinNonBlank(List.of(latestUser, recentUser, planAnchor));
    };
  }

  private static String latestUserMessage(final List<Event> events) {
    if (events == null || events.isEmpty()) {
      return "";
    }
    for (int i = events.size() - 1; i >= 0; i--) {
      final Content content = events.get(i).content().orElse(null);
      if (content == null || !"user".equals(content.role().orElse(""))) {
        continue;
      }
      final String text = content.text();
      if (StringUtils.isNotBlank(text)) {
        return text;
      }
    }
    return "";
  }

  private static String recentUserIntents(final List<Event> events, final int max) {
    if (events == null || events.isEmpty()) {
      return "";
    }
    final List<String> intents = new ArrayList<>();
    for (int i = events.size() - 1; i >= 0 && intents.size() < max; i--) {
      final Content content = events.get(i).content().orElse(null);
      if (content == null || !"user".equals(content.role().orElse(""))) {
        continue;
      }
      final String text = content.text();
      if (StringUtils.isNotBlank(text)) {
        intents.add(text);
      }
    }
    return String.join("\n", intents.reversed());
  }

  private static String planAnchor(final InvocationContext context) {
    final Plan plan = RunStateUtils.getState(context).plan();
    if (plan == null) {
      return "";
    }
    final String summary = PlanningUtils.buildPlanSummary(plan);
    final String taskFocus = PlanningUtils.buildTaskFocusPrompt(plan);
    return joinNonBlank(List.of(summary, taskFocus));
  }

  private static String joinNonBlank(final List<String> values) {
    return values.stream().filter(StringUtils::isNotBlank).reduce((a, b) -> a + "\n" + b).orElse("");
  }
}
