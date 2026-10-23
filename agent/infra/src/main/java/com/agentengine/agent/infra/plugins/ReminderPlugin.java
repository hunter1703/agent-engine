package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.agents.ContentUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Adds the agent's reminder map to each model request as a working-memory brief.
 *
 * <p>This plugin reads whatever reminders are registered in {@link SessionState}, groups them by
 * category, and renders each group as a titled section inside a brief that closes the request, after
 * the whole history, marked as coming from the runtime. The brief is rebuilt for every request and
 * is not stored in the session, so what changes between requests stays at the end and the history
 * before it is the same from one request to the next.
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

    llmRequestBuilder.contents(
        withRemindersBrief(
            llmRequestBuilder.build().contents(), buildBrief(sessionState.allReminders())));
    return Maybe.empty();
  }

  private static List<Content> withRemindersBrief(final List<Content> contents, final String brief) {
    if (StringUtils.isBlank(brief)) {
      return contents;
    }
    final Part remindersPart =
        Part.fromText(
            ContentUtils.systemNotice(
                """
                <reminders>
                %s
                </reminders>"""
                    .formatted(brief)));
    final List<Content> updated = new ArrayList<>(contents);
    updated.add(ContentUtils.userMessage(List.of(remindersPart)));
    return updated;
  }

  public static String buildBrief(final Collection<Reminder> reminders) {
    final StringBuilder sb = new StringBuilder();
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
      final String instruction = Reminder.instruction(entry.getKey());
      if (StringUtils.isNotBlank(instruction)) {
        sb.append(instruction).append("\n");
      }
      for (final Reminder reminder : entry.getValue()) {
        if (StringUtils.isNotBlank(reminder.message())) {
          sb.append("- ").append(reminder.message()).append("\n");
        }
      }
      hasContent = true;
    }

    return hasContent ? sb.toString().trim() : null;
  }

  private static int groupRank(final String group) {
    final int index = Reminder.GROUP_ORDER.indexOf(group);
    return index < 0 ? Reminder.GROUP_ORDER.size() : index;
  }
}
