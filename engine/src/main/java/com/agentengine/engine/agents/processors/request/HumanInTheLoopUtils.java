package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HumanInTheLoopUtils {
  public static final String REQUEST_CONFIRMATION_FUNCTION = "adk_request_confirmation";

  private HumanInTheLoopUtils() {}

  public static String findPendingConfirmationId(final InvocationContext context) {
    if (context == null || context.session() == null || CollectionUtils.isEmpty(context.session().events())) {
      return null;
    }
    final List<String> requested = new ArrayList<>();
    final Set<String> responded = new LinkedHashSet<>();
    for (final Event event : CollectionUtils.nullSafeList(context.session().events())) {
      if (event == null) {
        continue;
      }
      event.functionCalls().stream()
          .filter(functionCall -> functionCall.id().isPresent())
          .filter(
              functionCall ->
                  REQUEST_CONFIRMATION_FUNCTION.equals(functionCall.name().orElse(null)))
          .forEach(functionCall -> requested.add(functionCall.id().orElse("")));
      event.functionResponses().stream()
          .filter(functionResponse -> functionResponse.id().isPresent())
          .filter(
              functionResponse ->
                  REQUEST_CONFIRMATION_FUNCTION.equals(functionResponse.name().orElse(null)))
          .forEach(functionResponse -> responded.add(functionResponse.id().orElse("")));
    }
    for (int i = requested.size() - 1; i >= 0; i--) {
      final String id = requested.get(i);
      if (StringUtils.isNotBlank(id) && !responded.contains(id)) {
        return id;
      }
    }
    return null;
  }

  public static boolean isSameInvocationAsPause(
      final InvocationContext context, final String pauseInvocationId) {
    if (context == null || StringUtils.isBlank(context.invocationId())) {
      return false;
    }
    return StringUtils.isNotBlank(pauseInvocationId)
        && pauseInvocationId.equals(context.invocationId());
  }

  public static String buildMissingAnswerMessage(final String question) {
    final StringBuilder builder = new StringBuilder();
    builder.append("No clarification answer was provided.");
    if (StringUtils.isNotBlank(question)) {
      builder.append(" Ask the user to answer this question: '").append(question).append("'.");
    } else {
      builder.append(" Ask the user for the required clarification.");
    }
    builder.append(" Do not continue the task until clarification is provided.");
    return builder.toString();
  }
}
