package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;

import com.google.adk.events.Event;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionCall;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanningUtils {

  private PlanningUtils() {}

  /**
   * Builds a snapshot of the plan: each task's status and outcome. The task in progress is
   * described in full by {@link #buildTaskFocusPrompt}.
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
      sb.append(" (part of: ").append(task.getParent()).append(")");
    }
    if (StringUtils.isNotBlank(task.getResult())) {
      sb.append(" -> ").append(task.getResult());
    }
    return sb.append('\n').toString();
  }

  public static String buildTaskFocusPrompt(final Plan plan) {
    final Task activeLeaf = activeTask(plan);
    if (activeLeaf != null) {
      return "Active task:\n- " + formatTaskFocus(activeLeaf);
    }
    final Task nextTodo = findNextTodoTask(plan);
    return nextTodo == null ? "" : "Next task:\n- " + formatTaskFocus(nextTodo);
  }

  /**
   * Builds the per-turn planning reminder: the plan summary followed by a focus statement for
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

  /**
   * The task being worked on: the first task in progress. A plan keeps its tasks with the parts of a
   * task before the task, so this is the part being worked on, or a task whose parts are all done.
   */
  public static Task activeTask(final Plan plan) {
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

  /**
   * The plan {@code events} left, found by running their recorded planning calls again, in order,
   * through the planning tools in a context of no real run. A call that failed when it was made
   * fails again and changes nothing.
   */
  public static Plan recoverPlan(final List<Event> events, final PlanningValidator validator) {
    final Map<String, Tool> nameVsTool =
        Map.of(
            Constants.ToolNames.CREATE_PLAN, new CreatePlanTool(validator),
            Constants.ToolNames.ADD_TASK, new AddTaskTool(validator),
            Constants.ToolNames.COMPLETE_TASK, new CompleteTaskTool(validator),
            Constants.ToolNames.ABANDON_PLAN, new AbandonPlanTool());
    final SessionState recovered = new SessionState();
    final ToolContext toolContext =
        ToolContext.builder(SessionUtils.throwawayContext(recovered)).build();
    for (final Event event : CollectionUtils.nullSafeList(events)) {
      for (final FunctionCall call : event.functionCalls()) {
        final Tool tool = nameVsTool.get(call.name().orElse(null));
        if (tool != null) {
          tool.runAsync(call.args().orElse(Map.of()), toolContext).blockingGet();
        }
      }
    }
    return recovered.plan();
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
    if (newStatus == TaskStatus.ABANDONED) {
      abandonOpenDescendants(task, getTaskVsChildren(updatedPlan.getTasks()));
    }
    if (updatedPlan.allTasksTerminal()) {
      updatedPlan.finish(PlanStatus.DONE, result);
    }
    return updatedPlan;
  }

  /**
   * {@code tasks} as a plan holds them: all to do, and each parent named as the plan's task of that
   * name (whatever the case it was given in).
   */
  public static List<Task> normalizedTasks(final List<Task> tasks) {
    final List<Task> planTasks = CollectionUtils.nullSafeList(tasks);
    final Map<String, String> loweredNameVsName = new HashMap<>();
    for (final Task task : planTasks) {
      if (StringUtils.isNotBlank(task.getName())) {
        loweredNameVsName.put(task.getName().trim().toLowerCase(), task.getName());
      }
      task.setStatus(TaskStatus.TODO);
      task.setResult(null);
    }
    for (final Task task : planTasks) {
      if (StringUtils.isNotBlank(task.getParent())) {
        final String resolved = loweredNameVsName.get(task.getParent().trim().toLowerCase());
        if (resolved != null) {
          task.setParent(resolved);
        }
      }
    }
    return planTasks;
  }

  /** The plan of {@code tasks}, which {@link #normalizedTasks} made and are valid: the first actionable task, with its ancestors, in progress. */
  public static Plan buildPlan(final String title, final String goal, final List<Task> tasks) {
    final Plan plan = new Plan(title, goal, tasks);
    activateNextActionableLineage(plan);
    return plan;
  }

  /**
   * A to-do task for {@code plan}, its parent named as the plan names it (whatever the case it was
   * given in); a parent the plan lacks is kept as given, for the validator to report.
   */
  public static Task buildTask(
      final Plan plan,
      final String name,
      final String parent,
      final String goal,
      final String description) {
    final Task task = new Task(name, goal);
    if (StringUtils.isNotBlank(parent)) {
      final Task existingParent = findTaskByName(plan, parent);
      task.setParent(existingParent != null ? existingParent.getName() : parent);
    }
    if (StringUtils.isNotBlank(description)) {
      task.setDescription(description);
    }
    return task;
  }

  private static void abandonOpenDescendants(
      final Task task, final Map<String, List<Task>> parentVsChildren) {
    for (final Task child : CollectionUtils.nullSafeList(parentVsChildren.get(task.getName()))) {
      if (!isTerminalStatus(taskStatus(child))) {
        child.setStatus(TaskStatus.ABANDONED);
        child.setResult("Abandoned with its parent task '" + task.getName() + "'.");
      }
      abandonOpenDescendants(child, parentVsChildren);
    }
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
