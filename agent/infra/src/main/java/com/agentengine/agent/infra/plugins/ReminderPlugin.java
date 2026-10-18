package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.ContentUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Injects the agent's reminder map into the user's message as a working-memory brief.
 *
 * <p>This plugin reads whatever reminders are registered in {@link SessionState}, groups them by
 * category, and renders each group as a titled section inside a brief placed ahead of the user
 * message that started the run.
 *
 * <p>Once the current run holds a {@code refresh_reminders} result, that result carries the latest
 * resource reminders: the brief attached to the user message is updated to retain only live
 * execution state (such as the active plan), and every older refresh result is expired.
 */
public final class ReminderPlugin extends BasePlugin {

  private static final String EXPIRED_RESULT_MESSAGE =
      "Outdated reminders snapshot, superseded by newer reminders.";

  public ReminderPlugin() {
    super("reminder_plugin");
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {

    final SessionState sessionState =
        SessionUtils.getSessionState(callbackContext.invocationContext());

    final List<Content> contents = llmRequestBuilder.build().contents();
    final int userMessageIndex =
        CollectionUtils.findLastIndexAfter(contents, -1, ContentUtils::isUserSentMessage);
    if (userMessageIndex < 0) {
      return Maybe.empty();
    }

    final int latestRefreshRemindersResultIndex =
        CollectionUtils.findLastIndexAfter(
            contents, userMessageIndex, ReminderPlugin::isRefreshRemindersResult);
    // If a refresh_reminders call exists, only inject the session's local reminders (which are not
    // shared and cannot be invalidated by external entities)
    final Collection<Reminder> remindersToInject =
        latestRefreshRemindersResultIndex >= 0
            ? sessionState.localReminders()
            : sessionState.allReminders();
    final List<Content> withBrief =
        withRemindersBrief(contents, userMessageIndex, buildBrief(remindersToInject));
    llmRequestBuilder.contents(
        expireRefreshResultsExcept(withBrief, latestRefreshRemindersResultIndex));
    return Maybe.empty();
  }

  /**
   * Expires every {@code refresh_reminders} result except the one at {@code latestResultIndex}.
   * Calls are kept, so the history still shows what the model did; an expired result no longer
   * carries the reminders it once returned.
   */
  private static List<Content> expireRefreshResultsExcept(
      final List<Content> contents, final int latestResultIndex) {
    final List<Content> expired = new ArrayList<>();
    for (int i = 0; i < contents.size(); i++) {
      final Content content = contents.get(i);
      expired.add(
          i != latestResultIndex && isRefreshRemindersResult(content)
              ? withExpiredRefreshResults(content)
              : content);
    }
    return expired;
  }

  private static boolean isRefreshRemindersResult(final Content content) {
    return !ContentUtils.getFunctionResponses(content, Constants.ToolNames.REFRESH_REMINDERS)
        .isEmpty();
  }

  private static Content withExpiredRefreshResults(final Content content) {
    final List<Part> parts =
        content.parts().orElse(List.of()).stream()
            .map(
                part ->
                    ContentUtils.isFunctionResponse(part, Constants.ToolNames.REFRESH_REMINDERS)
                        ? expiredResultPart(part)
                        : part)
            .toList();
    return content.toBuilder().parts(parts).build();
  }

  private static Part expiredResultPart(final Part part) {
    final FunctionResponse result = part.functionResponse().orElseThrow();
    final Map<String, Object> expiredPayload =
        Map.of("status", "expired", "message", EXPIRED_RESULT_MESSAGE);
    return Part.builder()
        .functionResponse(result.toBuilder().response(expiredPayload).build())
        .build();
  }

  private static List<Content> withRemindersBrief(
      final List<Content> contents, final int userMessageIndex, final String brief) {
    if (StringUtils.isBlank(brief)) {
      return contents;
    }

    final Content userContent = contents.get(userMessageIndex);
    final Part remindersPart =
        Part.fromText(
            """
            <reminders>
            %s
            </reminders>

            """
                .formatted(brief));

    final List<Part> parts = new ArrayList<>();
    parts.add(remindersPart);
    parts.addAll(userContent.parts().orElse(List.of()));

    final List<Content> updated = new ArrayList<>(contents);
    updated.set(userMessageIndex, userContent.toBuilder().parts(parts).build());
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
