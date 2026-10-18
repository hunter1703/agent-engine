package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanningValidator {

  private PlanningValidator() {}

  public static String validatePlan(final Plan plan) {
    if (plan == null) {
      return "Plan is required.";
    }
    final String planLabel = describePlan(plan);
    if (plan.getStatus() == null || plan.getStatus() == PlanStatus.UNKNOWN) {
      return planLabel + " has unknown status.";
    }
    final Map<String, Task> nameVsTask = new LinkedHashMap<>();
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      final String name = PlanningUtils.taskName(task);
      if (StringUtils.isBlank(name)) {
        continue;
      }
      final Task existing = nameVsTask.putIfAbsent(name, task);
      if (existing != null) {
        return "Duplicate task name '"
            + name
            + "' found in "
            + planLabel
            + ".";
      }
    }
    final Map<String, List<Task>> parentVsTasks = PlanningUtils.groupTasksByParent(plan);
    final Task openTask = findFirstOpenTask(plan);
    if (plan.getStatus() != null
        && plan.getStatus() != PlanStatus.IN_PROGRESS
        && openTask != null) {
      return planLabel
          + " is "
          + describePlanStatus(plan)
          + " but "
          + describeTask(openTask)
          + " is "
          + describeTaskStatus(openTask)
          + ".";
    }
    if (!PlanningUtils.isTerminalStatus(plan.getStatus())
        && StringUtils.isNotBlank(plan.getResult())) {
      return planLabel + " is " + describePlanStatus(plan) + " but has a result.";
    }
    if (PlanningUtils.isTerminalStatus(plan.getStatus()) && StringUtils.isBlank(plan.getResult())) {
      return planLabel + " is " + describePlanStatus(plan) + " but is missing a result.";
    }
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      final String parent = task.getParent();
      if (StringUtils.isNotBlank(parent) && !nameVsTask.containsKey(parent)) {
        return describeTask(task) + " references missing parent task '" + parent + "'.";
      }
      final TaskStatus status = PlanningUtils.getTaskStatusEnum(task);
      if (status == TaskStatus.UNKNOWN) {
        return describeTask(task) + " has unknown status.";
      }
      if (!PlanningUtils.isTerminalStatus(status) && StringUtils.isNotBlank(task.getResult())) {
        return describeTask(task) + " is " + describeTaskStatus(task) + " but has a result.";
      }
      if (status == TaskStatus.IN_PROGRESS) {
        final String ancestorError = validateAncestorsInProgress(task, nameVsTask);
        if (ancestorError != null) {
          return ancestorError;
        }
      }
      if (PlanningUtils.isTerminalStatus(status)) {
        final Task blocking = PlanningUtils.findNonTerminalDescendant(task, parentVsTasks);
        if (blocking != null) {
          return describeTask(task)
              + " is "
              + status.getValue()
              + " but child tasks are not terminal (e.g., "
              + describeTask(blocking)
              + " is "
              + describeTaskStatus(blocking)
              + ").";
        }
        if (StringUtils.isBlank(task.getResult())) {
          return describeTask(task) + " is " + status.getValue() + " but is missing a result.";
        }
      }
    }
    final List<Task> inProgressTasks = collectInProgressTasks(plan, null, null, false);
    final String lineageError = validateSingleLineage(inProgressTasks, nameVsTask, null);
    if (lineageError != null) {
      return lineageError;
    }
    return validateInProgressOrder(plan, nameVsTask, parentVsTasks, inProgressTasks);
  }

  public static String canAddTask(final Plan plan, final Task task) {
    if (plan == null) {
      return "Plan is required.";
    }
    if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
      return describePlan(plan)
          + " is "
          + describePlanStatus(plan)
          + "; must be "
          + PlanStatus.IN_PROGRESS.getValue()
          + " to add tasks.";
    }
    if (task == null) {
      return "Task is required for " + describePlan(plan) + ".";
    }
    if (PlanningUtils.getTaskStatusEnum(task) == TaskStatus.UNKNOWN) {
      return describeTask(task) + " has unknown status.";
    }
    final Map<String, Task> nameVsTask = PlanningUtils.getTasksByName(plan);
    final String name = task.getName();
    if (StringUtils.isNotBlank(name) && nameVsTask.containsKey(name)) {
      return "Task name '"
          + name
          + "' already exists in "
          + describePlan(plan)
          + " (existing "
          + describeTask(nameVsTask.get(name))
          + ").";
    }
    final String parent = task.getParent();
    if (StringUtils.isNotBlank(parent)) {
      final Task parentTask = nameVsTask.get(parent);
      if (parentTask == null) {
        return describeTask(task) + " references missing parent task '" + parent + "'.";
      }
      if (PlanningUtils.isTerminalStatus(PlanningUtils.getTaskStatusEnum(parentTask))) {
        return "Parent "
            + describeTask(parentTask)
            + " is "
            + describeTaskStatus(parentTask)
            + "; cannot add child "
            + describeTask(task)
            + ".";
      }
    }
    final TaskStatus status = PlanningUtils.getTaskStatusEnum(task);
    if (status == TaskStatus.IN_PROGRESS) {
      if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
        return describePlan(plan)
            + " is "
            + describePlanStatus(plan)
            + "; must be "
            + PlanStatus.IN_PROGRESS.getValue()
            + " before starting tasks.";
      }
      final String ancestorError = validateAncestorsInProgress(task, nameVsTask);
      if (ancestorError != null) {
        return ancestorError;
      }
      final List<Task> inProgressTasks = collectInProgressTasks(plan, task, status, true);
      final String lineageError = validateSingleLineage(inProgressTasks, nameVsTask, task);
      if (lineageError != null) {
        return lineageError;
      }
      return validateInProgressOrder(
          plan, nameVsTask, PlanningUtils.groupTasksByParent(plan), inProgressTasks);
    }
    return null;
  }

  public static String canUpdateTask(
      final Plan plan, final Task task, final TaskStatus newStatus, final String result) {
    if (plan == null || task == null || newStatus == null) {
      return null;
    }
    if (newStatus == TaskStatus.UNKNOWN) {
      return describeTask(task) + " has unknown status.";
    }
    if (PlanningUtils.isTerminalStatus(newStatus) && StringUtils.isBlank(result)) {
      return "The 'result' field is required when marking a task as " + newStatus.getValue() + ".";
    }
    if (!PlanningUtils.isTerminalStatus(newStatus) && StringUtils.isNotBlank(result)) {
      return "The 'result' field can only be set when marking a task as "
          + PlanningUtils.getTerminalStatuses(TaskStatus.class)
          + ".";
    }
    if (PlanningUtils.isTerminalStatus(plan.getStatus())) {
      return describePlan(plan)
          + " is "
          + describePlanStatus(plan)
          + "; must be "
          + PlanStatus.IN_PROGRESS.getValue()
          + " to update tasks.";
    }
    final TaskStatus currentStatus = PlanningUtils.getTaskStatusEnum(task);
    if (currentStatus == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.TODO) {
      return describeTask(task)
          + " cannot move from "
          + TaskStatus.IN_PROGRESS.getValue()
          + " back to "
          + TaskStatus.TODO.getValue()
          + ".";
    }
    if (PlanningUtils.isTerminalStatus(currentStatus)
        && !PlanningUtils.isTerminalStatus(newStatus)) {
      return describeTask(task) + " is " + currentStatus.getValue() + "; cannot reopen it.";
    }

    final Map<String, Task> nameVsTask = PlanningUtils.getTasksByName(plan);
    final Map<String, List<Task>> taskTree = PlanningUtils.groupTasksByParent(plan);

    if (newStatus == TaskStatus.IN_PROGRESS) {
      if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
        return describePlan(plan)
            + " is "
            + describePlanStatus(plan)
            + "; must be "
            + PlanStatus.IN_PROGRESS.getValue()
            + " before starting tasks.";
      }
      final String ancestorError = validateAncestorsInProgress(task, nameVsTask);
      if (ancestorError != null) {
        return ancestorError;
      }
      if (currentStatus != TaskStatus.IN_PROGRESS) {
        final String orderError = validateNextTaskToStart(plan, task, taskTree);
        if (orderError != null) {
          return orderError;
        }
      }
    }

    if (newStatus == TaskStatus.TODO) {
      final Task descendant = findInProgressDescendant(task, taskTree);
      if (descendant != null) {
        return describeTask(task)
            + " cannot move to todo while child "
            + describeTask(descendant)
            + " is "
            + TaskStatus.IN_PROGRESS.getValue()
            + ".";
      }
    }

    if (PlanningUtils.isTerminalStatus(newStatus)) {
      final Task blocking = PlanningUtils.findNonTerminalDescendant(task, taskTree);
      if (blocking != null) {
        return describeTask(task)
            + " cannot be marked "
            + newStatus.getValue()
            + " while child tasks are not terminal (e.g., "
            + describeTask(blocking)
            + " is "
            + describeTaskStatus(blocking)
            + ").";
      }
      if (StringUtils.isBlank(result)) {
        return describeTask(task)
            + " is being marked "
            + newStatus.getValue()
            + " but is missing a result.";
      }
    }

    final List<Task> inProgressTasks = collectInProgressTasks(plan, task, newStatus, false);
    final String lineageError = validateSingleLineage(inProgressTasks, nameVsTask, task);
    if (lineageError != null) {
      return lineageError;
    }
    return validateInProgressOrder(plan, nameVsTask, taskTree, inProgressTasks);
  }

  @Deprecated
  public static String canUpdateTask(final Plan plan, final Task task, final TaskStatus newStatus) {
    return canUpdateTask(plan, task, newStatus, null);
  }

  public static String getPrematureCompleteViolation(final Plan plan) {
    if (plan == null) {
      return null;
    }
    final PlanStatus status = plan.getStatus();
    if (!PlanningUtils.isTerminalStatus(status)) {
      final Task openTask = findFirstOpenTask(plan);
      final String openTaskMessage =
          openTask == null ? "" : " and has open tasks (e.g., " + describeTask(openTask) + ")";
      return "Cannot submit final answer while "
          + describePlan(plan)
          + " is "
          + describePlanStatus(plan)
          + openTaskMessage
          + ". Please complete your plan first.";
    }
    return null;
  }

  private static Task findFirstOpenTask(final Plan plan) {
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      if (isOpenTask(task)) {
        return task;
      }
    }
    return null;
  }

  private static boolean isOpenTask(final Task task) {
    final TaskStatus status = PlanningUtils.getTaskStatusEnum(task);
    return status == TaskStatus.TODO
        || status == TaskStatus.IN_PROGRESS
        || status == TaskStatus.UNKNOWN;
  }

  private static String validateAncestorsInProgress(
      final Task task, final Map<String, Task> nameVsTask) {
    if (task == null) {
      return null;
    }
    String parent = task.getParent();
    while (StringUtils.isNotBlank(parent)) {
      final Task parentTask = nameVsTask.get(parent);
      if (parentTask == null) {
        return describeTask(task) + " references missing parent task '" + parent + "'.";
      }
      if (PlanningUtils.getTaskStatusEnum(parentTask) != TaskStatus.IN_PROGRESS) {
        return "Parent "
            + describeTask(parentTask)
            + " must be "
            + TaskStatus.IN_PROGRESS.getValue()
            + " before starting child "
            + describeTask(task)
            + ".";
      }
      parent = parentTask.getParent();
    }
    return null;
  }

  private static Task findInProgressDescendant(
      final Task task, final Map<String, List<Task>> taskTree) {
    final String taskName = PlanningUtils.taskName(task);
    for (Task child : CollectionUtils.nullSafeList(taskTree.get(taskName))) {
      if (PlanningUtils.getTaskStatusEnum(child) == TaskStatus.IN_PROGRESS) {
        return child;
      }
      final Task nested = findInProgressDescendant(child, taskTree);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static String validateNextTaskToStart(
      final Plan plan, final Task task, final Map<String, List<Task>> taskTree) {
    final Task nextTask = PlanningUtils.findNextTodoTask(plan);
    if (nextTask == null) {
      return "No pending tasks remain to start in " + describePlan(plan) + ".";
    }
    if (nextTask == task) {
      return null;
    }
    final String taskName = PlanningUtils.taskName(task);
    final String nextName = nextTask.getName();
    if (StringUtils.isNotBlank(taskName) && taskName.equals(nextName)) {
      return null;
    }
    return "Tasks are started in plan order, so "
        + describeTask(task)
        + " cannot be started yet: the next task to start is "
        + describeTask(nextTask)
        + ".";
  }

  private static String validateInProgressOrder(
      final Plan plan,
      final Map<String, Task> nameVsTask,
      final Map<String, List<Task>> taskTree,
      final List<Task> inProgressTasks) {
    if (CollectionUtils.isEmpty(inProgressTasks)) {
      return null;
    }
    final Task nextTodo = PlanningUtils.findNextTodoTask(plan);
    if (nextTodo == null) {
      return null;
    }
    final Set<String> lineage = collectLineageIds(nextTodo, nameVsTask);
    for (Task task : inProgressTasks) {
      final String taskName = PlanningUtils.taskName(task);
      if (StringUtils.isBlank(taskName)) {
        return "In-progress " + describeTask(task) + " is missing a name.";
      }
      if (!lineage.contains(taskName)) {
        return describeTask(task)
            + " is still in progress, so no other task can be started or completed yet. Carry on"
            + " with its work and then complete it with its result, or complete it now if its work"
            + " is already done. The next task to start after that is "
            + describeTask(nextTodo)
            + " in "
            + describePlan(plan)
            + ".";
      }
    }
    return null;
  }

  private static List<Task> collectInProgressTasks(
      final Plan plan,
      final Task updatedTask,
      final TaskStatus newStatus,
      final boolean includeMissingTask) {
    final List<Task> inProgress = new ArrayList<>();
    final String updatedName = PlanningUtils.taskName(updatedTask);
    boolean updatedFound = false;
    for (Task task : CollectionUtils.nullSafeList(plan.getTasks())) {
      TaskStatus status = PlanningUtils.getTaskStatusEnum(task);
      if (task == updatedTask
          || (StringUtils.isNotBlank(updatedName)
              && updatedName.equals(task.getName()))) {
        status = newStatus;
        updatedFound = true;
      }
      if (status == TaskStatus.IN_PROGRESS) {
        inProgress.add(task);
      }
    }
    if (includeMissingTask && !updatedFound && newStatus == TaskStatus.IN_PROGRESS) {
      inProgress.add(updatedTask);
    }
    return inProgress;
  }

  /**
   * {@code actedOn} is the task being started or completed, or null when a whole plan is checked.
   */
  private static String validateSingleLineage(
      final List<Task> inProgressTasks, final Map<String, Task> nameVsTask, final Task actedOn) {
    if (inProgressTasks.size() <= 1) {
      return null;
    }
    final Task deepest = findDeepestTask(inProgressTasks, nameVsTask);
    if (deepest == null) {
      return null;
    }
    final Set<String> lineageIds = collectLineageIds(deepest, nameVsTask);
    for (Task task : inProgressTasks) {
      final String taskName = PlanningUtils.taskName(task);
      if (StringUtils.isBlank(taskName)) {
        return "In-progress " + describeTask(task) + " is missing a name.";
      }
      if (!lineageIds.contains(taskName)) {
        if (actedOn == null) {
          return "The plan marks "
              + describeTask(task)
              + " and "
              + describeTask(deepest)
              + " as in progress in different branches, but only one branch can be in progress at"
              + " a time. Mark at most one of them as in progress.";
        }
        final Task blocking = taskName.equals(actedOn.getName()) ? deepest : task;
        return describeTask(actedOn)
            + " cannot be started or completed while "
            + describeTask(blocking)
            + " is still in progress. Carry on with the work of "
            + describeTask(blocking)
            + " and then complete it with its result, or complete it now if its work is already"
            + " done, and then retry.";
      }
    }
    return null;
  }

  private static Task findDeepestTask(final List<Task> tasks, final Map<String, Task> nameVsTask) {
    Task deepest = null;
    int maxDepth = -1;
    for (Task task : tasks) {
      final int depth = taskDepth(task, nameVsTask);
      if (depth > maxDepth) {
        maxDepth = depth;
        deepest = task;
      }
    }
    return deepest;
  }

  private static int taskDepth(final Task task, final Map<String, Task> nameVsTask) {
    int depth = 0;
    String parent = task == null ? null : task.getParent();
    final Set<String> visited = new HashSet<>();
    while (StringUtils.isNotBlank(parent) && visited.add(parent)) {
      final Task parentTask = nameVsTask.get(parent);
      if (parentTask == null) {
        break;
      }
      depth++;
      parent = parentTask.getParent();
    }
    return depth;
  }

  private static Set<String> collectLineageIds(final Task task, final Map<String, Task> nameVsTask) {
    final Set<String> lineage = new HashSet<>();
    Task current = task;
    while (current != null) {
      final String taskName = current.getName();
      if (StringUtils.isBlank(taskName) || !lineage.add(taskName)) {
        break;
      }
      final String parent = current.getParent();
      if (StringUtils.isBlank(parent)) {
        break;
      }
      current = nameVsTask.get(parent);
    }
    return lineage;
  }

  private static String describePlan(final Plan plan) {
    if (plan == null) {
      return "plan <unknown>";
    }
    final String id = plan.getPlanId();
    final String title = plan.getTitle();
    if (StringUtils.isNotBlank(id) && StringUtils.isNotBlank(title)) {
      return "plan " + id + " (" + title + ")";
    }
    if (StringUtils.isNotBlank(id)) {
      return "plan " + id;
    }
    if (StringUtils.isNotBlank(title)) {
      return "plan " + title;
    }
    return "plan <unknown>";
  }

  private static String describePlanStatus(final Plan plan) {
    if (plan == null || plan.getStatus() == null) {
      return PlanStatus.UNKNOWN.getValue();
    }
    return plan.getStatus().getValue();
  }

  private static String describeTask(final Task task) {
    final String name = PlanningUtils.taskName(task);
    if (StringUtils.isBlank(name)) {
      return "task <unknown>";
    }
    return "task '" + name + "'";
  }

  private static String describeTaskStatus(final Task task) {
    return PlanningUtils.getTaskStatusEnum(task).getValue();
  }
}
