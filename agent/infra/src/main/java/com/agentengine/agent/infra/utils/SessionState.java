package com.agentengine.agent.infra.utils;

import com.agentengine.agent.infra.notebook.NotebookService;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.planning.PlanningValidator;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.knowledge.api.beans.IndexingStatus;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SessionState {

  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm:ss", Locale.ENGLISH);

  private RunState runState;
  private ZonedDateTime userTime;
  private final Map<String, Set<Reminder>> groupVsSharedReminders = new LinkedHashMap<>();
  // The child sessions this session spawned, each with its agent and the goal it was given.
  private final Map<String, SpawnedAgent> childSessionIdVsSpawnedAgent = new HashMap<>();
  private Plan plan;
  private String scratchpadSummary = "";

  public static SessionState buildFrom(final Session session, final PlanningValidator planningValidator) {
    final SessionState state = new SessionState();
    if (session == null) {
      return state;
    }
    final List<Event> events = session.events();
    state.setRunState(RunState.buildFrom(events));
    state.updatePlan(PlanningUtils.recoverPlan(events, planningValidator));
    state.addRemindersFrom(events);
    return state;
  }

  /**
   * Announces the non-indexed knowledge items accessible to the session, which cannot be searched
   * semantically and must be read by ID. Indexed knowledge is searched with a query, so it needs no
   * reminder.
   */
  public void syncKnowledgeReminders(final KnowledgeService knowledgeService) {
    groupVsSharedReminders.remove(Reminder.GROUP_KNOWLEDGE_IDS);
    final Query nonIndexed =
        new Query()
            .withFilter(
                Filters.eq(Knowledge.FIELD_INDEXING_STATUS, IndexingStatus.NON_INDEXED.name()))
            .withPage(Page.UNBOUNDED);
    for (final Knowledge knowledge : knowledgeService.findByQuery(nonIndexed).getItems()) {
      addKnowledgeIdReminder(
          knowledge.getId(), "[%s] (non-indexed — omit query)".formatted(knowledge.getTitle()));
    }
  }

  public void syncNotebookReminder(final NotebookService notebookService) {
    groupVsSharedReminders.remove(Reminder.ID_NOTEBOOK_ACCESS);
    notebookService
        .summaries(true)
        .forEach(
            (notebookId, text) ->
                addReminder(new Reminder(Reminder.ID_NOTEBOOK_ACCESS, notebookId, text)));
    syncScratchpadReminder(notebookService);
  }

  /**
   * Refreshes the scratchpad's reminder from its local copy. The scratchpad is written only by this
   * session, so its reminder is kept apart from the notebooks that are re-read from storage.
   */
  public void syncScratchpadReminder(final NotebookService notebookService) {
    scratchpadSummary = notebookService.scratchpadSummary();
  }

  /** Remembers a child session this session spawned. */
  public void addSpawnedAgent(
      final String childSessionId, final String agentId, final String goal, boolean awaited) {
    final SpawnedAgent spawned = new SpawnedAgent(agentId, childSessionId, goal, awaited);
    childSessionIdVsSpawnedAgent.put(childSessionId, spawned);
  }

  /** Updates a child session this session spawned with a new goal. */
  public void updateSpawnedAgent(final String childSessionId, final String goal, boolean awaited) {
    final SpawnedAgent spawned = childSessionIdVsSpawnedAgent.get(childSessionId);
    if (spawned != null) {
      addSpawnedAgent(spawned.sessionId(), spawned.agentId(), goal, awaited);
    }
  }

  public void markSpawnedAgentAwaited(final String childSessionId) {
    final SpawnedAgent spawned = childSessionIdVsSpawnedAgent.get(childSessionId);
    if (spawned != null) {
      childSessionIdVsSpawnedAgent.put(
          childSessionId,
          new SpawnedAgent(spawned.agentId(), spawned.sessionId(), spawned.goal(), true));
    }
  }

  /** The agent of a child session this session spawned; null for any other session. */
  public String spawnedAgentId(final String childSessionId) {
    final SpawnedAgent spawned = childSessionIdVsSpawnedAgent.get(childSessionId);
    return spawned == null ? null : spawned.agentId();
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
  }

  public void updateUserTime(final ZonedDateTime time) {
    this.userTime = time;
  }

  public String formatCurrentTimeInUserZone() {
    final ZoneId zone = userTime != null ? userTime.getZone() : ZoneId.of("UTC");
    return formatTime(ZonedDateTime.now(zone));
  }

  public List<Reminder> localReminders() {
    final List<Reminder> local = new ArrayList<>();
    if (userTime != null) {
      local.add(userContextReminder(userTime));
    }
    if (hasActivePlan()) {
      local.add(
          new Reminder(
              Reminder.ID_ACTIVE_PLAN,
              Reminder.ID_ACTIVE_PLAN,
              PlanningUtils.buildPlanReminder(plan)));
    }
    for (final SpawnedAgent spawned : childSessionIdVsSpawnedAgent.values()) {
      local.add(spawnedAgentReminder(spawned));
    }
    local.add(new Reminder(Reminder.ID_NOTEBOOK_ACCESS, "scratchpad", scratchpadSummary));
    return List.copyOf(local);
  }

  public List<Reminder> sharedReminders() {
    final List<Reminder> all = new ArrayList<>();
    for (final Set<Reminder> reminders : groupVsSharedReminders.values()) {
      all.addAll(reminders);
    }
    return List.copyOf(all);
  }

  public Collection<Reminder> allReminders() {
    return CollectionUtils.union(localReminders(), sharedReminders());
  }

  public void addReminder(final Reminder reminder) {
    if (reminder == null) {
      return;
    }
    final Set<Reminder> set =
        groupVsSharedReminders.computeIfAbsent(reminder.group(), k -> new LinkedHashSet<>());
    set.remove(reminder);
    set.add(reminder);
  }

  private static Reminder userContextReminder(final ZonedDateTime time) {
    return new Reminder(
        Reminder.ID_USER_CONTEXT,
        Reminder.ID_USER_CONTEXT,
        "Current date and time: " + formatTime(time));
  }

  private static String formatTime(final ZonedDateTime time) {
    final ZoneId zone = time.getZone();
    final String offset = time.getOffset().getId().replace("Z", "+00:00");
    final String zoneLabel =
        zone.getId().equals("UTC") ? "UTC" + offset : zone.getId() + ", UTC" + offset;
    return TIME_FORMAT.format(time) + " (" + zoneLabel + ")";
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
    final Map<String, String> sessionIdVsAgentId = new HashMap<>();
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
        final boolean spawn = response.name().orElse("").equals(Constants.ToolNames.SPAWN_AGENT);
        final String sessionId =
            CollectionUtils.getStringValueFromMap(
                spawn ? result : callArgs, Constants.ToolArgs.CHILD_SESSION_ID);
        if (StringUtils.isBlank(sessionId)) {
          continue;
        }
        sessionIdVsAwaited.put(sessionId, await == null || await);
        if (spawn) {
          sessionIdVsAgentId.put(
              sessionId,
              CollectionUtils.getStringValueFromMap(callArgs, Constants.ToolArgs.AGENT_ID));
        }
        sessionIdVsGoal.put(
            sessionId, CollectionUtils.getStringValueFromMap(callArgs, Constants.ToolArgs.GOAL));
      }
      if (response.name().orElse("").equals(Constants.ToolNames.AWAIT_AGENT)) {
        final String awaitedSession =
            CollectionUtils.getStringValueFromMap(callArgs, Constants.ToolArgs.CHILD_SESSION_ID);
        sessionIdVsAwaited.put(awaitedSession, true);
      }
    }

    for (final Map.Entry<String, String> entry : sessionIdVsGoal.entrySet()) {
      final String sessionId = entry.getKey();
      final String agentId = sessionIdVsAgentId.getOrDefault(sessionId, spawnedAgentId(sessionId));
      if (agentId != null) {
        addSpawnedAgent(sessionId, agentId, entry.getValue(), sessionIdVsAwaited.get(sessionId));
      }
    }
  }

  private void setRunState(RunState runState) {
    this.runState = runState;
  }

  private static Reminder spawnedAgentReminder(final SpawnedAgent spawnedAgent) {
    final String message =
        spawnedAgent.awaited()
            ? "[AWAITED] agent : '%s', session_id : '%s', goal : '%s'"
                .formatted(spawnedAgent.agentId(), spawnedAgent.sessionId(), spawnedAgent.goal())
            : "[NOT AWAITED] agent : '%s', session_id : '%s', goal : '%s'. Await it when you need its result."
                .formatted(spawnedAgent.agentId(), spawnedAgent.sessionId(), spawnedAgent.goal());
    return new Reminder(Reminder.GROUP_SPAWNED_AGENTS, spawnedAgent.sessionId(), message);
  }

  private record SpawnedAgent(String agentId, String sessionId, String goal, boolean awaited) {}
}
