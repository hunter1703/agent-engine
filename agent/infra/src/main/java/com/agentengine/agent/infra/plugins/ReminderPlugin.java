package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;

import java.util.*;
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
    final List<Reminder> initialReminders = sessionState.initialReminders();
    final List<Reminder> currentReminders = sessionState.reminders();

    if (CollectionUtils.isEmpty(initialReminders) && CollectionUtils.isEmpty(currentReminders)) {
      return Maybe.empty();
    }

    String brief = buildBrief(initialReminders);
    if (!initialReminders.equals(currentReminders) && StringUtils.isNotBlank(brief)) {
      brief =
          "> [!WARNING]\n"
              + "> **Historical Snapshot:** These reminders were captured at the start of the current run. "
              + "They have been superseded by subsequent `refresh_reminders` tool calls. Check the tool outputs for the current state.\n\n"
              + brief;
    }

    final List<Content> contents = llmRequestBuilder.build().contents();
    final List<Content> updatedContents = scrubRefreshReminders(appendToLatestUserTurn(contents, brief));
    llmRequestBuilder.contents(updatedContents);
    return Maybe.empty();
  }

  private static List<Content> scrubRefreshReminders(final List<Content> contents) {
    final int lastUserIndex = ContentUtils.findLatestUserContentIndex(contents);
    if (lastUserIndex < 0) {
      return contents;
    }

    // Find the LAST refresh_reminders response in the current run
    int lastRefreshResponseIndex = -1;
    for (int i = contents.size() - 1; i > lastUserIndex; i--) {
      final Content content = contents.get(i);
      if (isRefreshRemindersResponse(content)) {
        lastRefreshResponseIndex = i;
        break;
      }
    }

    final List<Content> scrubbed = new ArrayList<>();
    for (int i = 0; i < contents.size(); i++) {
      final Content content = contents.get(i);
      final boolean isRefreshCall = isRefreshRemindersCall(content);
      final boolean isRefreshResponse = isRefreshRemindersResponse(content);

      if (isRefreshCall || isRefreshResponse) {
        if (i < lastUserIndex) {
          // Drop entirely if from a previous run
          continue;
        } else if (isRefreshResponse && i != lastRefreshResponseIndex) {
          // Replace payload with expired message if it's an older one in the current run
          scrubbed.add(expireRefreshResponse(content));
          continue;
        }
      }
      scrubbed.add(content);
    }
    return scrubbed;
  }

  private static boolean isRefreshRemindersCall(final Content content) {
    return !ContentUtils.getFunctionCalls(content, Constants.ToolNames.REFRESH_REMINDERS).isEmpty();
  }

  private static boolean isRefreshRemindersResponse(final Content content) {
    return !ContentUtils.getFunctionResponses(content, Constants.ToolNames.REFRESH_REMINDERS).isEmpty();
  }

  private static Content expireRefreshResponse(final Content content) {
    final List<Part> updatedParts = new ArrayList<>();
    for (final Part part : content.parts().orElse(List.of())) {
      final FunctionResponse functionResponse = part.functionResponse().orElse(null);
      if (functionResponse != null && Constants.ToolNames.REFRESH_REMINDERS.equals(functionResponse.name().orElse(""))) {
        final Map<String, Object> newPayload = new HashMap<>(functionResponse.response().orElse(Map.of()));
        newPayload.put("status", "expired");
        newPayload.put("message", "This historical snapshot is expired. See the latest refresh_reminders result.");
        final FunctionResponse expiredResponse =
            FunctionResponse.builder()
                .name(functionResponse.name().orElse(""))
                .response(newPayload)
                .build();
        updatedParts.add(Part.builder().functionResponse(expiredResponse).build());
      } else {
        updatedParts.add(part);
      }
    }
    return content.toBuilder().parts(updatedParts).build();
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
    final String formatted = formatReminders(reminders);
    if (formatted == null) {
      return null;
    }
    return """

            ---
            [Reference material for this request, not part of the user's message. Use what applies, skip the rest.]
            """ + formatted;
  }

  public static String formatReminders(final List<Reminder> reminders) {
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
