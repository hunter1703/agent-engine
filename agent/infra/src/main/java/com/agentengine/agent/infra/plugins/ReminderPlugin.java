package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.Reminder;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Injects the agent's reminder map into the LLM request as a working-memory brief.
 *
 * <p>This plugin reads whatever reminders are currently registered in {@link SessionState}, groups
 * them by group, and renders each group as a titled section inside a structured brief.
 */
public final class ReminderPlugin extends BasePlugin {

  public ReminderPlugin() {
    super("reminder_plugin");
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {

    final SessionState sessionState =
        SessionUtils.getSessionState(callbackContext.invocationContext());
    final List<Reminder> reminders = sessionState.reminders();

    if (reminders.isEmpty()) {
      return Maybe.empty();
    }

    final String brief = buildBrief(reminders);
    if (StringUtils.isBlank(brief)) {
      return Maybe.empty();
    }

    llmRequestBuilder.appendInstructions(List.of(brief));
    return Maybe.empty();
  }

  private static String buildBrief(final List<Reminder> reminders) {
    final StringBuilder sb = new StringBuilder();
    sb.append(
        """
                ## Reminders — orient yourself before acting
                """);

    boolean hasContent = false;
    final Map<String, List<Reminder>> reminderGroups =
        CollectionUtils.transformToMultiValuedMap(reminders, Reminder::group, Function.identity());
    final List<Entry<String, List<Reminder>>> orderedGroups =
        reminderGroups.entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> groupRank(entry.getKey())))
            .toList();
    for (final Entry<String, List<Reminder>> entry : orderedGroups) {
      final String title = Reminder.title(entry.getKey());
      sb.append("\n### ").append(title).append("\n");
      for (final Reminder reminder : entry.getValue()) {
        if (StringUtils.isNotBlank(reminder.message())) {
          sb.append("- ").append(reminder.message()).append("\n");
        }
      }
      hasContent = true;
    }

    if (!hasContent) {
      return null;
    }

    sb.append(
        """

                ---
                Before acting: account for all items above in your next step.
                """);

    return sb.toString().trim();
  }

  private static int groupRank(final String group) {
    final int index = Reminder.GROUP_ORDER.indexOf(group);
    return index < 0 ? Reminder.GROUP_ORDER.size() : index;
  }
}
