package com.agentengine.agent.infra.utils;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.knowledge.api.beans.IndexingStatus;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SessionState {

  private RunState runState;
  private final Set<Reminder> reminders = new LinkedHashSet<>();
  private Plan plan;

  public static SessionState buildFrom(final List<Event> events) {
    final SessionState state = new SessionState();
    state.setRunState(RunState.buildFrom(events));
    state.updatePlan(PlanningUtils.buildFrom(events));
    state.addRemindersFrom(events);
    return state;
  }

  public void syncKnowledgeReminders(final KnowledgeService knowledgeService) {
    final List<Knowledge> knowledges =
        knowledgeService.findByQuery(new Query().withPage(Page.UNBOUNDED)).getItems();
    for (final Knowledge knowledge : knowledges) {
      if (IndexingStatus.NON_INDEXED.name().equals(knowledge.getIndexingStatus())) {
        addKnowledgeIdReminder(
            knowledge.getId(), "[%s] (non-indexed — omit query)".formatted(knowledge.getTitle()));
      } else {
        final String description = knowledge.getDescription();
        final String contentPreview = knowledge.getContentPreview();
        addKnowledgeIdReminder(
            knowledge.getId(),
            "["
                + knowledge.getTitle()
                + "]"
                + (StringUtils.isNotBlank(description) ? " " + description : "")
                + (StringUtils.isNotBlank(contentPreview)
                    ? " (sample excerpt: \"" + contentPreview + "\")"
                    : ""));
      }
    }
  }

  public void syncNotebookReminder(final NotebookService notebookService) {
    final String summary = notebookService.summary();
    if (StringUtils.isBlank(summary)) {
      return;
    }
    addReminder(
        new Reminder(
            Reminder.GROUP_NOTEBOOK_ACCESS,
            Reminder.ID_NOTEBOOK_ACCESS,
            "**When a tool asks for a notebook id or a note title, use the one shown here VERBATIM — any changes to it will NOT resolve and the tool call will FAIL.**\n"
                + summary));
  }

  public void addSpawnedAgentReminder(
      final String childSessionId, final String goal, boolean awaited) {
    final Reminder reminder =
        new Reminder(
            Reminder.GROUP_SPAWNED_AGENTS,
            childSessionId,
            spawnedAgentReminderMessage(childSessionId, goal, awaited));
    addReminder(reminder);
  }

  public void markSpawnedAgentAwaited(final String childSessionId) {
    if (!reminders.contains(new Reminder(Reminder.GROUP_SPAWNED_AGENTS, childSessionId, null))) {
      return;
    }
    addReminder(
        new Reminder(
            Reminder.GROUP_SPAWNED_AGENTS,
            childSessionId,
            spawnedAgentReminderMessage(childSessionId, null, true)));
  }

  /**
   * {@code hint} can run long (a sampled excerpt spans the whole document), so the id — the one
   * piece of this line a caller must actually use verbatim as a tool argument — goes right next to
   * the name it's naturally read alongside, not appended after however much text {@code hint}
   * happens to carry. A model given only the name would otherwise reach for that as if it were the
   * id, since nothing else nearby looks like one.
   */
  public void addKnowledgeIdReminder(final String knowledgeId, final String hint) {
    final Reminder reminder =
        new Reminder(
            Reminder.GROUP_KNOWLEDGE_IDS, knowledgeId, "(id: %s) %s".formatted(knowledgeId, hint));
    addReminder(reminder);
  }

  public RunState runState() {
    return runState;
  }

  public Plan plan() {
    return plan;
  }

  public boolean hasActivePlan() {
    return plan != null && !plan.getStatus().isTerminal();
  }

  public void updatePlan(final Plan plan) {
    this.plan = plan;
    if (!hasActivePlan()) {
      removeReminder(Reminder.ID_ACTIVE_PLAN);
      return;
    }
    addReminder(
        new Reminder(
            Reminder.GROUP_ACTIVE_PLAN,
            Reminder.ID_ACTIVE_PLAN,
            PlanningUtils.activePlanBrief(plan)));
  }

  public List<Reminder> reminders() {
    return List.copyOf(reminders);
  }

  public void addReminder(final Reminder reminder) {
    if (reminder == null) {
      return;
    }
    reminders.remove(reminder);
    reminders.add(reminder);
  }

  public void removeReminder(final String id) {
    reminders.removeIf(reminder -> Objects.equals(id, reminder.id()));
  }

  private Reminder findReminder(final String group, final String id) {
    return reminders.stream()
        .filter(
            reminder ->
                Objects.equals(group, reminder.group()) && Objects.equals(id, reminder.id()))
        .findFirst()
        .orElse(null);
  }

  private void addRemindersFrom(final List<Event> events) {
    // A FunctionCall and its FunctionResponse always land in separate Events, so the id lookup
    // must accumulate across the whole history, not reset per event.
    final Map<String, FunctionCall> idVsFunctionCall = new HashMap<>();
    for (final Event event : CollectionUtils.nullSafeList(events)) {
      final Content content = event.content().orElse(null);
      if (content == null) {
        continue;
      }
      addToolCallReminders(content, idVsFunctionCall);
    }
  }

  /**
   * Reconstructs reminders whose source of truth is the session's own event history, for every tool
   * call/response pair this session made that a fresh {@link SessionState} — rebuilt on actor
   * rehydration, with no memory of what was added to it live — needs to remember: spawned/messaged
   * child sessions.
   */
  private void addToolCallReminders(
      final Content content, final Map<String, FunctionCall> idVsFunctionCall) {
    final Map<String, Boolean> sessionIdVsAwaited = new HashMap<>();
    final Map<String, String> sessionIdVsGoal = new HashMap<>();

    for (final Part part : content.parts().orElse(List.of())) {
      final FunctionCall functionCall = part.functionCall().orElse(null);
      if (functionCall != null) {
        final String functionName = functionCall.name().orElse("");
        if (Constants.ToolNames.isAgentRoutingTool(functionName)) {
          functionCall.id().ifPresent(id -> idVsFunctionCall.put(id, functionCall));
        }
      }
      final FunctionResponse response = part.functionResponse().orElse(null);
      if (response == null) {
        continue;
      }
      final FunctionCall matchedCall = idVsFunctionCall.get(response.id().orElse(""));
      if (matchedCall == null) {
        continue;
      }
      final Map<String, Object> result = response.response().orElse(Map.of());
      final Map<String, Object> callArgs = matchedCall.args().orElse(Map.of());
      if (response.name().orElse("").equals(Constants.ToolNames.SPAWN_AGENT)
          || response.name().orElse("").equals(Constants.ToolNames.SEND_MESSAGE)) {
        final Boolean await =
            CollectionUtils.getBooleanValueFromMap(callArgs, Constants.ToolArgs.AWAIT_COMPLETION);
        final String sessionId =
            CollectionUtils.getStringValueFromMap(result, Constants.ToolArgs.CHILD_SESSION_ID);
        sessionIdVsAwaited.put(sessionId, await == null || await);
        sessionIdVsGoal.put(
            sessionId, CollectionUtils.getStringValueFromMap(callArgs, Constants.ToolArgs.GOAL));
      }
      if (response.name().orElse("").equals(Constants.ToolNames.AWAIT_AGENT)) {
        final String awaitedSession =
            CollectionUtils.getStringValueFromMap(callArgs, Constants.ToolArgs.CHILD_SESSION_ID);
        sessionIdVsAwaited.put(awaitedSession, true);
      }
    }

    for (Map.Entry<String, String> entry : sessionIdVsGoal.entrySet()) {
      final String sessionId = entry.getKey();
      final String goal = entry.getValue();
      final boolean awaited = sessionIdVsAwaited.get(sessionId);

      addSpawnedAgentReminder(sessionId, goal, awaited);
    }
  }

  private void setRunState(RunState runState) {
    this.runState = runState;
  }

  private static String spawnedAgentReminderMessage(
      final String childSessionId, final String goal, final boolean awaited) {
    final String agentId = SessionUtils.agentIdFromSessionId(childSessionId);
    if (awaited) {
      return "[AWAITED] agent : '%s', session_id : '%s', goal : '%s'"
          .formatted(agentId, childSessionId, goal);
    }
    return "[NOT AWAITED] agent : '%s', session_id : '%s', goal : '%s'. Use %s when you need its result."
        .formatted(agentId, childSessionId, goal, Constants.ToolNames.AWAIT_AGENT);
  }
}
