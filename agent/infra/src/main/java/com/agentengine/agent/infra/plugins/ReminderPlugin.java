package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.ContentUtils;
import com.agentengine.agent.infra.utils.Reminder;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Injects the agent's reminder map into the latest user turn as a working-memory brief.
 *
 * <p>This plugin reads whatever reminders are currently registered in {@link SessionState}, groups
 * them by group, and renders each group as a titled section inside a structured brief appended to
 * the most recent user-role {@link Content} — so the brief reads as context accompanying the
 * current request rather than as a standing rule in the system instruction.
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

    final List<Content> contents = llmRequestBuilder.build().contents();
    final List<Content> updatedContents = appendToLatestUserTurn(contents, brief);
    if (updatedContents != contents) {
      llmRequestBuilder.contents(updatedContents);
    }
    return Maybe.empty();
  }

  private static List<Content> appendToLatestUserTurn(
      final List<Content> contents, final String brief) {
    final int lastUserIndex = ContentUtils.findLatestUserContentIndex(contents);
    if (lastUserIndex < 0) {
      return contents;
    }

    final Content userContent = contents.get(lastUserIndex);
    final List<Part> parts = new ArrayList<>(userContent.parts().orElse(List.of()));
    parts.add(Part.fromText(brief));

    final List<Content> updated = new ArrayList<>(contents);
    updated.set(lastUserIndex, userContent.toBuilder().parts(parts).build());
    return updated;
  }

  private static String buildBrief(final List<Reminder> reminders) {
    final StringBuilder sb = new StringBuilder();
    sb.append(
        """

                ---
                [Reference material for this request, not part of the user's message. Use what applies, skip the rest.]
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
