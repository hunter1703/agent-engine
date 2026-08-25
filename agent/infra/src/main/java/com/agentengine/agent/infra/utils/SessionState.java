package com.agentengine.agent.infra.utils;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.knowledge.ReadKnowledgeSourceTool;
import com.agentengine.agent.infra.tools.knowledge.SearchKnowledgeTool;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.services.KnowledgeService;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.BaseAgentState;
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

public final class SessionState extends BaseAgentState {

  private final KnowledgeService knowledgeService;
  private RunState runState;
  private final Set<Reminder> reminders = new LinkedHashSet<>();
  private Plan plan;

  public SessionState(KnowledgeService knowledgeService) {
    this.knowledgeService = knowledgeService;
  }

  public static SessionState buildFrom(
      final List<Event> events, final KnowledgeService knowledgeService) {
    final SessionState state = new SessionState(knowledgeService);
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
                      Constants.ARG_SOURCE,
                      source)));
    }
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
                    Constants.ARG_KNOWLEDGE_ID,
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
    for (final Event event : CollectionUtils.nullSafeList(events)) {
      final Content content = event.content().orElse(null);
      if (content == null) {
        continue;
      }
      addSpawnedAgentsReminders(content);
    }
  }

  private void addSpawnedAgentsReminders(final Content content) {
    final Map<String, FunctionCall> idVsFunctionCalls = new HashMap<>();
    final Map<String, Boolean> sessionIdVsAwaited = new HashMap<>();
    final Map<String, String> sessionIdVsGoal = new HashMap<>();

    for (final Part part : content.parts().orElse(List.of())) {
      final FunctionCall functionCall = part.functionCall().orElse(null);
      if (functionCall != null) {
        final String functionName = functionCall.name().orElse("");
        if (functionName.equals(Constants.SPAWN_AGENT_TOOL_NAME)
            || functionName.equals(Constants.SEND_MESSAGE_TOOL_NAME)
            || functionName.equals(Constants.AWAIT_AGENT_TOOL_NAME)) {
          functionCall.id().ifPresent(id -> idVsFunctionCalls.put(id, functionCall));
        }
      }
      final FunctionResponse response = part.functionResponse().orElse(null);
      if (response != null) {
        final Map<String, Object> result = response.response().orElse(Map.of());
        if (response.name().orElse("").equals(Constants.SPAWN_AGENT_TOOL_NAME)) {
          final FunctionCall spawnAgentCall = idVsFunctionCalls.get(response.id().orElse(""));
          final Map<String, Object> callArgs = spawnAgentCall.args().orElse(Map.of());
          final Boolean await =
              CollectionUtils.getBooleanValueFromMap(callArgs, Constants.ARG_AWAIT_COMPLETION);
          final String spawnedSession =
              CollectionUtils.getStringValueFromMap(result, Constants.ARG_CHILD_SESSION_ID);
          sessionIdVsAwaited.put(spawnedSession, await == null || await);
          sessionIdVsGoal.put(
              spawnedSession, CollectionUtils.getStringValueFromMap(callArgs, "goal"));
        }
        if (response.name().orElse("").equals(Constants.SEND_MESSAGE_TOOL_NAME)) {
          final FunctionCall sendMessageCall = idVsFunctionCalls.get(response.id().orElse(""));
          final Map<String, Object> callArgs = sendMessageCall.args().orElse(Map.of());
          final Boolean await =
              CollectionUtils.getBooleanValueFromMap(callArgs, Constants.ARG_AWAIT_COMPLETION);
          final String sessionId =
              CollectionUtils.getStringValueFromMap(result, Constants.ARG_CHILD_SESSION_ID);
          sessionIdVsAwaited.put(sessionId, await == null || await);
          sessionIdVsGoal.put(sessionId, CollectionUtils.getStringValueFromMap(callArgs, "goal"));
        }
        if (response.name().orElse("").equals(Constants.AWAIT_AGENT_TOOL_NAME)) {
          final FunctionCall spawnAgentCall = idVsFunctionCalls.get(response.id().orElse(""));
          final Map<String, Object> callArgs = spawnAgentCall.args().orElse(Map.of());
          final String awaitedSession =
              CollectionUtils.getStringValueFromMap(callArgs, Constants.ARG_CHILD_SESSION_ID);
          sessionIdVsAwaited.put(awaitedSession, true);
        }
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
    final String goalClause = StringUtils.isNotBlank(goal) ? " goal='" + goal + "'" : "";
    if (awaited) {
      return "[READ] agent_session='%s'%s — already awaited.".formatted(childSessionId, goalClause);
    }
    return "[UNREAD] agent_session='%s'%s — not yet awaited. Use %s with %s='%s' when you need its result."
        .formatted(
            childSessionId,
            goalClause,
            Constants.AWAIT_AGENT_TOOL_NAME,
            Constants.ARG_CHILD_SESSION_ID,
            childSessionId);
  }
}
