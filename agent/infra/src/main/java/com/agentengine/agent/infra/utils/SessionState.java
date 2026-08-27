package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.NotebookGrants;
import com.agentengine.agent.api.utils.NotebookUtils;
import com.agentengine.agent.infra.notebook.Note;
import com.agentengine.agent.infra.notebook.NotebookRepository;
import com.agentengine.agent.infra.notebook.NotesRepository;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.knowledge.ReadKnowledgeSourceTool;
import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.Permission;
import com.agentengine.util.common.query.*;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SessionState {

  private final KnowledgeService knowledgeService;
  private final NotesRepository notesRepository;
  private RunState runState;
  private final Set<Reminder> reminders = new LinkedHashSet<>();
  private Plan plan;

  public SessionState(KnowledgeService knowledgeService, NotesRepository notesRepository) {
    this.knowledgeService = knowledgeService;
    this.notesRepository = notesRepository;
  }

  public static SessionState buildFrom(
      final List<Event> events,
      final KnowledgeService knowledgeService,
      final NotebookRepository notebookRepository,
      final NotesRepository notesRepository) {
    final SessionState state = new SessionState(knowledgeService, notesRepository);
    state.setRunState(RunState.buildFrom(events));
    state.updatePlan(PlanningUtils.buildFrom(events));
    state.addRemindersFrom(events);
    return state;
  }

  public void addKnowledgeSourceReminders(final Collection<String> sources) {
    for (final String source : CollectionUtils.nullSafeList(sources)) {
      addReminder(
          new Reminder(
              Reminder.GROUP_KNOWLEDGE_SOURCES,
              source,
              "%s Use %s with %s='%s' to read it."
                  .formatted(
                      source,
                      ReadKnowledgeSourceTool.DESCRIPTOR.name(),
                      Constants.ToolArgs.SOURCE,
                      source)));
    }
  }

  public void addNotebookReminders(final NotebookGrants notebookGrants) {
    if (notebookGrants == null || CollectionUtils.isEmpty(notebookGrants.grants())) {
      return;
    }

    final Map<String, NotebookSummary> summaries = new HashMap<>();

    for (final Map.Entry<String, Permission> entry :
        CollectionUtils.nullSafeMap(notebookGrants.grants()).entrySet()) {
      final String key = entry.getKey();
      final Permission permission = entry.getValue();

      if (NotebookUtils.isNoteId(key)) {
        String notebookId = NotebookUtils.notebookIdOf(key);
        String noteTitle = NotebookUtils.noteTitleOf(key);

        NotebookSummary summary = summaries.computeIfAbsent(notebookId, k -> new NotebookSummary());
        if (permission == Permission.WRITE) {
          summary.writePermissionedNotes.add(noteTitle);
        } else if (permission == Permission.READ) {
          summary.readPermissionedNotes.add(noteTitle);
        }
      } else {
        NotebookSummary summary = summaries.computeIfAbsent(key, k -> new NotebookSummary());
        if (permission == Permission.CREATE) {
          summary.canCreate = true;
        } else if (permission == Permission.READ) {
          summary.canReadNotebook = true;
        }
      }
    }

    final List<String> createPermissionedNotebookIds = new ArrayList<>();
    for (Map.Entry<String, NotebookSummary> entry : summaries.entrySet()) {
      if (entry.getValue().canCreate) {
        createPermissionedNotebookIds.add(entry.getKey());
      }
    }

    if (CollectionUtils.isNotEmpty(createPermissionedNotebookIds)) {
      final Filter filter = Filters.in(Note.FIELD_NOTEBOOK_ID, createPermissionedNotebookIds);
      final Query query = new Query().withFilter(filter).withPage(new Page(0, 1000));
      final PaginatedResult<Note> notesResult = notesRepository.findByQuery(query);
      for (Note note : notesResult.getItems()) {
        summaries
            .computeIfAbsent(note.getNotebookId(), _ -> new NotebookSummary())
            .writePermissionedNotes
            .add(note.getNoteTitle());
      }
    }

    final StringBuilder sb = new StringBuilder("Notebook Permissions:");
    for (Map.Entry<String, NotebookSummary> entry : summaries.entrySet()) {
      String nb = entry.getKey();
      NotebookSummary summary = entry.getValue();
      sb.append("\n- Notebook '").append(nb).append("': ");

      if (summary.canCreate) {
        sb.append("You have permission to CREATE new notes.");
      } else {
        sb.append("You CANNOT CREATE new notes.");
      }

      if (summary.canReadNotebook) {
        sb.append(" You have general READ access to the notebook.");
      }

      if (!summary.writePermissionedNotes.isEmpty() || !summary.readPermissionedNotes.isEmpty()) {
        if (!summary.writePermissionedNotes.isEmpty()) {
          sb.append("\n  - READ/WRITE notes: ")
              .append(String.join(", ", summary.writePermissionedNotes));
        }
        if (!summary.readPermissionedNotes.isEmpty()) {
          sb.append("\n  - READ ONLY notes: ")
              .append(String.join(", ", summary.readPermissionedNotes));
        }
      }
    }

    addReminder(
        new Reminder(
            Reminder.GROUP_NOTEBOOK_GRANTS, Reminder.GROUP_NOTEBOOK_GRANTS, sb.toString()));
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
            Reminder.GROUP_KNOWLEDGE_IDS,
            knowledgeId,
            "%s Use %s with %s='%s' to search it."
                .formatted(
                    hint,
                    SearchKnowledgeTool.DESCRIPTOR.name(),
                    Constants.ToolArgs.KNOWLEDGE_ID,
                    knowledgeId));
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

  private void addRemindersFrom(final List<Event> events) {
    // A FunctionCall and its FunctionResponse always land in separate Events, so the id lookup
    // must accumulate across the whole history, not reset per event.
    final Map<String, FunctionCall> idVsFunctionCall = new HashMap<>();
    for (final Event event : CollectionUtils.nullSafeList(events)) {
      final Content content = event.content().orElse(null);
      if (content == null) {
        continue;
      }
      addSpawnedAgentsReminders(content, idVsFunctionCall);
    }
  }

  private void addSpawnedAgentsReminders(
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
        sessionIdVsGoal.put(sessionId, CollectionUtils.getStringValueFromMap(callArgs, "goal"));
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
    return "[NOT AWAITED] agent : '%s', session_id : '%s', goal : '%s'. Use %s with %s='%s' when you need its result."
        .formatted(
            agentId,
            childSessionId,
            goal,
            Constants.ToolNames.AWAIT_AGENT,
            Constants.ToolArgs.CHILD_SESSION_ID,
            childSessionId);
  }

  private static class NotebookSummary {
    boolean canCreate = false;
    boolean canReadNotebook = false;
    final Set<String> readPermissionedNotes = new LinkedHashSet<>();
    final Set<String> writePermissionedNotes = new LinkedHashSet<>();
  }
}
