package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;

import java.util.*;

public final class PlanningUtils {

  private PlanningUtils() {}

  /**
   * Builds a complete snapshot of the plan with all task details.
   * Injected into the LLM context to help the agent track progress.
   */
  public static String buildPlanSummary(final Plan plan) {
    if (plan == null) {
      return "No active plan found.";
    }
    final StringBuilder sb = new StringBuilder();
    sb.append("Plan: ").append(plan.getTitle());
    sb.append(" [").append(plan.getStatus().getValue()).append("]");
    sb.append("\nGoal: ").append(plan.getGoal());
    if (StringUtils.isNotBlank(plan.getResult())) {
      sb.append("\nResult: ").append(plan.getResult());
    }
    // Full task tree with all details
    sb.append("\n\nTasks:\n");
    for (final Task task : plan.getTasks()) {
      sb.append(taskSummary(task));
    }
    return sb.toString().trim();
  }

  private static String taskSummary(final Task task) {
    final StringBuilder sb = new StringBuilder();
    final String marker =
        switch (taskStatus(task)) {
          case DONE -> "[x]";
          case IN_PROGRESS -> "[>]";
          case ABANDONED -> "[-]";
          default -> "[ ]";
        };
    sb.append("- ").append(marker).append(" ").append(task.getName());
    if (StringUtils.isNotBlank(task.getParent())) {
      sb.append(" (parent: ").append(task.getParent()).append(")");
    }
    sb.append("\n  Goal: ").append(task.getGoal() != null ? task.getGoal() : "");
    if (StringUtils.isNotBlank(task.getDescription())) {
      sb.append("\n  Description: ").append(task.getDescription());
    }
    if (StringUtils.isNotBlank(task.getResult())) {
      sb.append("\n  Result: ").append(task.getResult());
    }
    return sb.toString();
  }

  public static String buildTaskFocusPrompt(final Plan plan) {
    final Task activeLeaf = getActiveLeaf(plan);
    if (activeLeaf != null) {
      return "TASK LOOP: Continue until all tasks are done or abandoned. FOCUS TASK:\n- "
          + formatTaskFocus(activeLeaf)
          + "\nWork only on this task until it is completed. Do not start working on other tasks "
          + "before marking this one complete and advancing to the next task.";
    }
    final Task nextTodo = findNextTodoTask(plan);
    if (nextTodo != null) {
      return "TASK LOOP: Continue until all tasks are done or abandoned. NEXT TASK:\n- "
          + formatTaskFocus(nextTodo)
          + "\nStart working on this task now. Do not start other tasks before completing it.";
    }
    return "";
  }

  /**
   * Builds the per-turn planning reminder: the full plan summary followed by a focus statement for
   * the active task (or the next task to start).
   */
  public static String buildPlanReminder(final Plan plan) {
    if (plan == null) {
      return "";
    }
    final String summary = buildPlanSummary(plan);
    final String focus = buildTaskFocusPrompt(plan);
    return StringUtils.joinNonBlank(List.of(summary, focus));
  }

  public static Plan buildFrom(final List<Event> events) {
    final Map<String, FunctionCall> pendingCallsByCallId = new HashMap<>();
    for (final Event event : CollectionUtils.nullSafeList(events)) {
      final Content content = event.content().orElse(null);
      if (content == null) {
        continue;
      }
      for (final Part part : content.parts().orElse(List.of())) {
        final FunctionCall call = part.functionCall().orElse(null);
        if (call == null) {
          continue;
        }
        final String toolName = call.name().orElse(null);
        if (!Constants.ToolNames.isPlanningTool(toolName)) {
          continue;
        }
        final String callId = call.id().orElse(null);
        if (callId == null) {
          continue;
        }
        pendingCallsByCallId.put(callId, call);
      }
    }
    Plan plan = null;
    for (final Event event : CollectionUtils.nullSafeList(events)) {
      final Content content = event.content().orElse(null);
      if (content == null) {
        continue;
      }
      for (final Part part : content.parts().orElse(List.of())) {
        final FunctionResponse response = part.functionResponse().orElse(null);
        if (response == null) {
          continue;
        }
        final String callId = response.id().orElse(null);
        final FunctionCall pendingCall = callId == null ? null : pendingCallsByCallId.get(callId);
        if (pendingCall == null) {
          continue;
        }
        final Map<String, Object> responseMap = response.response().orElse(Map.of());
        if (responseMap.containsKey("error")) {
          continue;
        }
        plan = applyReplayedEvent(
            plan,
            pendingCall.name().orElse(""),
            pendingCall.args().orElse(Map.of()),
            responseMap);
      }
    }
    return plan;
  }

  public static Plan applyTaskUpdate(
      final Plan plan, final String taskName, TaskStatus newStatus, String result) {
    final Plan updatedPlan = new Plan(plan);
    final Task task = findTaskByName(updatedPlan, taskName);
    if (task == null) {
      return updatedPlan;
    }
    newStatus = newStatus != null ? newStatus : task.getStatus();
    if (!isTerminalStatus(newStatus)) {
      result = null;
    } else {
      result = StringUtils.isNotBlank(result) ? result : task.getResult();
    }
    task.setStatus(newStatus);
    task.setResult(result);
    if (updatedPlan.allTasksTerminal()) {
      updatedPlan.finish(PlanStatus.DONE, result);
    }
    return updatedPlan;
  }

  /**
   * Finds the next actionable task in post-order and activates it (plus its ancestor lineage).
   *
   * <p>Post-order visits children before parents, so the first non-terminal task is either:
   * <ul>
   *   <li>A {@code TODO} leaf — mark it and its ancestors {@code IN_PROGRESS}, return the lineage.
   *   <li>An {@code IN_PROGRESS} parent whose children are all terminal — already active, surface it.
   * </ul>
   */
  public static List<Task> activateNextActionableLineage(final Plan plan) {
    if (plan == null) {
      return List.of();
    }
    List<Task> tasks = plan.getTasks();
    final Map<String, Task> nameVsTask = CollectionUtils.transformToMap(tasks, Task::getName);
    for (final Task task : tasks) {
      final TaskStatus status = taskStatus(task);
      if (isTerminalStatus(status)) {
        continue;
      }
      if (status == TaskStatus.IN_PROGRESS) {
        return List.of(task);
      }
      final List<Task> lineage = new ArrayList<>(resolveAncestors(task.getParent(), nameVsTask));
      lineage.add(task);
      for (final Task ancestor : lineage) {
        ancestor.setStatus(TaskStatus.IN_PROGRESS);
      }
      return List.copyOf(lineage);
    }
    return List.of();
  }

  public static Task findTaskByName(final Plan plan, final String name) {
    return plan != null ? plan.getTask(name) : null;
  }

  public static Task findNextTodoTask(final Plan plan) {
    if (plan == null) {
      return null;
    }
    for (final Task task : plan.getTasks()) {
      if (taskStatus(task) == TaskStatus.TODO) {
        return task;
      }
    }
    return null;
  }

  public static Task findFirstOpenTask(final Plan plan) {
    for (final Task task : plan.getTasks()) {
      if (isOpenTask(task)) {
        return task;
      }
    }
    return null;
  }

  public static Task findNonTerminalDescendant(
      final Task task, final Map<String, List<Task>> taskTree) {
    for (final Task child : CollectionUtils.nullSafeList(taskTree.get(task.getName()))) {
      if (!isTerminalStatus(taskStatus(child))) {
        return child;
      }
      final Task nested = findNonTerminalDescendant(child, taskTree);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  public static List<String> taskNames(final List<Task> tasks) {
    if (CollectionUtils.isEmpty(tasks)) {
      return List.of();
    }
    return tasks.stream().map(Task::getName).toList();
  }

  public static TaskStatus taskStatus(final Task task) {
    if (task == null || task.getStatus() == null) {
      return TaskStatus.TODO;
    }
    return task.getStatus();
  }

  public static boolean isTerminalStatus(final TaskStatus status) {
    return status != null && status.isTerminal();
  }

  public static boolean isTerminalStatus(final PlanStatus status) {
    return status != null && status.isTerminal();
  }

  public static boolean isOpenTask(final Task task) {
    final TaskStatus status = taskStatus(task);
    return status == TaskStatus.TODO || status == TaskStatus.IN_PROGRESS || status == TaskStatus.UNKNOWN;
  }

  public static String planLabel(final Plan plan) {
    return "plan " + plan.getTitle();
  }

  public static String describeTask(final Task task) {
    return "task '" + task.getName() + "'";
  }

  private static Plan applyReplayedEvent(
      Plan currentPlan,
      final String toolName,
      final Map<String, Object> args,
      final Map<String, Object> responseMap) {
    if (Constants.ToolNames.CREATE_PLAN.equals(toolName)) {
      return JsonUtils.fromMap(
          CollectionUtils.getMapFromMap(responseMap, "createdPlan"), Plan.class);
    }
    if (Constants.ToolNames.ADD_TASK.equals(toolName)) {
      currentPlan = AddTaskTool.applyAddTask(
          currentPlan,
          CollectionUtils.getStringValueFromMap(args, Constants.ToolArgs.PARENT),
          CollectionUtils.getStringValueFromMap(args, "name"),
          CollectionUtils.getStringValueFromMap(args, "goal"),
          CollectionUtils.getStringValueFromMap(args, "description"));
    } else if (Constants.ToolNames.COMPLETE_TASK.equals(toolName)) {
      final String task = CollectionUtils.getStringValueFromMap(args, "task");
      final String taskResult = CollectionUtils.getStringValueFromMap(args, "result");
      final String rawStatus = CollectionUtils.getStringValueFromMap(args, "status");
      final TaskStatus taskStatus =
          StringUtils.isNotBlank(rawStatus)
              ? TaskStatus.valueOfOrDefault(rawStatus)
              : TaskStatus.DONE;
      currentPlan = applyTaskUpdate(currentPlan, task, taskStatus, taskResult);
      if (isTerminalStatus(taskStatus) && !currentPlan.getStatus().isTerminal()) {
        activateNextActionableLineage(currentPlan);
      }
    }
    return currentPlan;
  }

  private static String formatTaskFocus(final Task task) {
    final StringBuilder sb = new StringBuilder();
    sb.append("[").append(taskStatus(task).getValue()).append("] ").append(task.getName());
    if (StringUtils.isNotBlank(task.getParent())) {
      sb.append(" (parent: ").append(task.getParent()).append(")");
    }
    sb.append(" | goal: ").append(task.getGoal());
    if (StringUtils.isNotBlank(task.getDescription())) {
      sb.append(" | description: ").append(task.getDescription());
    }
    return sb.toString();
  }

  private static Task getActiveLeaf(final Plan plan) {
    if (plan == null) {
      return null;
    }
    for (final Task task : plan.getTasks()) {
      if (taskStatus(task) == TaskStatus.IN_PROGRESS) {
        return task;
      }
    }
    return null;
  }

  private static List<Task> resolveAncestors(
      final String parent, final Map<String, Task> nameVsTask) {
    final List<Task> ancestors = new ArrayList<>();
    final Set<String> visited = new HashSet<>();
    String current = parent;
    while (StringUtils.isNotBlank(current) && visited.add(current)) {
      final Task parentTask = nameVsTask.get(current);
      if (parentTask == null) {
        break;
      }
      ancestors.addFirst(parentTask);
      current = parentTask.getParent();
    }
    return ancestors;
  }

  public static Map<String, List<Task>> getTaskVsChildren(final List<Task> tasks) {
    final Map<String, List<Task>> parentVsChildren = new LinkedHashMap<>();
    for (final Task task : tasks) {
      final String parent = task.getParent();
      if (StringUtils.isNotBlank(parent)) {
        parentVsChildren.computeIfAbsent(parent, _ -> new ArrayList<>()).add(task);
      }
    }
    return parentVsChildren;
  }
}
