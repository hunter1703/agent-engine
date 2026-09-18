package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.Permission;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SessionState {

  private final KnowledgeService knowledgeService;
  private RunState runState;
  private final Set<Reminder> reminders = new LinkedHashSet<>();
  private Plan plan;

  public SessionState(KnowledgeService knowledgeService) {
    this.knowledgeService = knowledgeService;
  }

  public static SessionState buildFrom(
      final List<Event> events,
      final KnowledgeService knowledgeService,
      final NotebookRepository notebookRepository) {
    final SessionState state = new SessionState(knowledgeService);
    state.setRunState(RunState.buildFrom(events));
    state.updatePlan(PlanningUtils.buildFrom(events));
    state.addRemindersFrom(events);
    return state;
  }

  public void addKnowledgeSourceReminders(final Collection<String> sources) {
    for (final String source : CollectionUtils.nullSafeList(sources)) {
      addReminder(new Reminder(Reminder.GROUP_KNOWLEDGE_SOURCES, source, source));
    }
  }

  public void addNotebookReminders(final NotebookGrants notebookGrants) {
    if (notebookGrants == null || CollectionUtils.isEmpty(notebookGrants.grants())) {
      return;
    }
    final Reminder existing =
        findReminder(Reminder.GROUP_NOTEBOOK_GRANTS, Reminder.GROUP_NOTEBOOK_GRANTS);
    final Map<String, Permission> merged = new HashMap<>();
    if (existing != null) {
      for (final Map.Entry<String, Object> entry : existing.details().entrySet()) {
        merged.put(entry.getKey(), (Permission) entry.getValue());
      }
    }
    merged.putAll(notebookGrants.grants());
    final NotebookGrants mergedGrants = new NotebookGrants(merged);
    addReminder(
        new Reminder(
            Reminder.GROUP_NOTEBOOK_GRANTS,
            Reminder.GROUP_NOTEBOOK_GRANTS,
            "Editing a note implies reading it.\n**When a tool asks for a notebook id or a note id, use the id shown here VERBATIM — any changes to it will NOT resolve and tool call will FAIL.**\n"
                + mergedGrants.describe(),
            new HashMap<>(merged)));
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

  public void addKnowledgeIdReminders(final Collection<String> knowledgeIds) {
    if (CollectionUtils.isEmpty(knowledgeIds)) {
      return;
    }
    final Map<String, Knowledge> knowledges = knowledgeService.findByIds(knowledgeIds);
    for (final Knowledge knowledge : knowledges.values()) {
      final String description = knowledge.getDescription();
      addKnowledgeIdReminder(
          knowledge.getId(),
          "["
              + knowledge.getTitle()
              + "]"
              + (StringUtils.isNotBlank(description) ? " " + description : ""));
    }
  }

  public void addKnowledgeIdReminder(final String knowledgeId, final String hint) {
    final Reminder reminder =
        new Reminder(
            Reminder.GROUP_KNOWLEDGE_IDS, knowledgeId, "%s (id: %s)".formatted(hint, knowledgeId));
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
   * child sessions, and notebooks/notes this session created and therefore owns (ownership is
   * checked separately from the {@link NotebookGrants} map, so it's otherwise invisible here; see
   * {@link #addNotebookReminder}).
   */
  private void addToolCallReminders(
      final Content content, final Map<String, FunctionCall> idVsFunctionCall) {
    final Map<String, Boolean> sessionIdVsAwaited = new HashMap<>();
    final Map<String, String> sessionIdVsGoal = new HashMap<>();

    for (final Part part : content.parts().orElse(List.of())) {
      final FunctionCall functionCall = part.functionCall().orElse(null);
      if (functionCall != null) {
        final String functionName = functionCall.name().orElse("");
        if (Constants.ToolNames.isAgentRoutingTool(functionName)
            || Constants.ToolNames.CREATE_NOTEBOOK.equals(functionName)
            || Constants.ToolNames.CREATE_OR_UPDATE_NOTE.equals(functionName)) {
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
      if (response.name().orElse("").equals(Constants.ToolNames.CREATE_NOTEBOOK)
          && Constants.ToolStatus.SUCCESS.equals(
              CollectionUtils.getStringValueFromMap(result, Constants.ToolStatus.STATUS))) {
        addNotebookReminders(
            NotebookGrants.ofNotebook(
                CollectionUtils.getStringValueFromMap(result, Constants.ToolArgs.NOTEBOOK_ID)));
      }
      if (response.name().orElse("").equals(Constants.ToolNames.CREATE_OR_UPDATE_NOTE)
          && Constants.ToolStatus.PENDING.equals(
              CollectionUtils.getStringValueFromMap(result, Constants.ToolStatus.STATUS))) {
        final String notebookId =
            CollectionUtils.getStringValueFromMap(callArgs, Constants.ToolArgs.NOTEBOOK_ID);
        final String noteTitle =
            CollectionUtils.getStringValueFromMap(callArgs, Constants.ToolArgs.NOTE_TITLE);
        addNotebookReminders(NotebookGrants.ofNote(notebookId, noteTitle, Permission.WRITE));
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
