package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.validation.ValidationCollector;
import com.agentengine.util.common.validation.Validator;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.agentengine.agent.infra.tools.planning.PlanningUtils.describeTask;
import static com.agentengine.agent.infra.tools.planning.PlanningUtils.planLabel;

@Singleton
public class PlanningValidator implements Validator<Plan> {

  /**
   * Static convenience method for validating a plan. Returns the first error message if
   * validation fails, or null if the plan is valid.
   */
  public static String validatePlan(final Plan plan) {
    final PlanningValidator instance = new PlanningValidator();
    final ValidationCollector collector = new ValidationCollector();
    instance.validate(plan, collector);
    final List<String> errors = collector.errors();
    return errors.isEmpty() ? null : errors.get(0);
  }

  @Override
  public Class<Plan> targetType() {
    return Plan.class;
  }

  @Override
  public void validate(final Plan plan, final ValidationCollector errors) {
    if (plan == null) {
      errors.add("Plan is required.");
      return;
    }
    if (plan.getStatus() == null || plan.getStatus() == PlanStatus.UNKNOWN) {
      errors.add(planLabel(plan) + " has unknown status.");
      return;
    }
    if (StringUtils.isBlank(plan.getTitle())) {
      errors.add("Plan has blank title.");
    }
    if (StringUtils.isBlank(plan.getGoal())) {
      errors.add(planLabel(plan) + " has blank goal.");
    }
    if (CollectionUtils.isEmpty(plan.getTasks())) {
      errors.add(planLabel(plan) + " has no tasks.");
      return;
    }
    final List<Task> tasks = plan.getTasks();
    final Map<String, List<Task>> parentVsTasks = PlanningUtils.getTaskVsChildren(plan.getTasks());
    final Task openTask = PlanningUtils.findFirstOpenTask(plan);
    if (plan.getStatus() != PlanStatus.IN_PROGRESS && openTask != null) {
      errors.add(planLabel(plan) + " is " + plan.getStatus().getValue()
          + " but " + PlanningUtils.describeTask(openTask) + " is " + PlanningUtils.taskStatus(openTask).getValue() + ".");
    }
    if (!PlanningUtils.isTerminalStatus(plan.getStatus()) && StringUtils.isNotBlank(plan.getResult())) {
      errors.add(planLabel(plan) + " is " + plan.getStatus().getValue() + " but has a result.");
    }
    if (PlanningUtils.isTerminalStatus(plan.getStatus()) && StringUtils.isBlank(plan.getResult())) {
      errors.add(planLabel(plan) + " is " + plan.getStatus().getValue() + " but is missing a result.");
    }
    final Map<String, Task> nameVsTask = CollectionUtils.transformToMap(tasks, Task::getName);
    for (final Task task : tasks) {
      final String parent = task.getParent();
      if (StringUtils.isBlank(task.getName())) {
        errors.add("Tasks cannot have blank title.");
        continue;
      }
      if (StringUtils.isBlank(task.getGoal())) {
        errors.add(describeTask(task) + " has blank goal");
      }
      if (StringUtils.isNotBlank(parent) && !nameVsTask.containsKey(parent)) {
        errors.add(PlanningUtils.describeTask(task) + " references missing parent task '" + parent + "'.");
      }
      final TaskStatus status = PlanningUtils.taskStatus(task);
      if (status == TaskStatus.UNKNOWN) {
        errors.add(PlanningUtils.describeTask(task) + " has unknown status.");
      }
      if (status != TaskStatus.UNKNOWN && !PlanningUtils.isTerminalStatus(status) && StringUtils.isNotBlank(task.getResult())) {
        errors.add(PlanningUtils.describeTask(task) + " is " + PlanningUtils.taskStatus(task).getValue() + " but has a result.");
      }
      if (status == TaskStatus.IN_PROGRESS) {
        errors.add(validateAncestorsInProgress(task, nameVsTask));
      }
      if (PlanningUtils.isTerminalStatus(status)) {
        final Task blocking = PlanningUtils.findNonTerminalDescendant(task, parentVsTasks);
        if (blocking != null) {
          errors.add(PlanningUtils.describeTask(task) + " is " + status.getValue()
              + " but child tasks are not terminal (e.g., "
              + PlanningUtils.describeTask(blocking) + " is " + PlanningUtils.taskStatus(task).getValue() + ").");
        }
        if (StringUtils.isBlank(task.getResult())) {
          errors.add(PlanningUtils.describeTask(task) + " is " + status.getValue() + " but is missing a result.");
        }
      }
    }
    final List<Task> inProgressTasks = getInProgressTasks(plan, null, null, false);
    errors.add(validateSingleLineage(inProgressTasks, nameVsTask, null));
    errors.add(validateInProgressOrder(plan, nameVsTask, inProgressTasks));
  }

  public String canAddTask(final Plan plan, final Task task) {
    if (plan == null) {
      return "Plan is required.";
    }
    if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
      return planLabel(plan) + " is " + plan.getStatus().getValue()
          + "; must be " + PlanStatus.IN_PROGRESS.getValue() + " to add tasks.";
    }
    if (task == null) {
      return "Task is required for " + planLabel(plan) + ".";
    }
    if (PlanningUtils.taskStatus(task) == TaskStatus.UNKNOWN) {
      return PlanningUtils.describeTask(task) + " has unknown status.";
    }
    final Map<String, Task> nameVsTask = CollectionUtils.transformToMap(plan.getTasks(), Task::getName);
    if (nameVsTask.containsKey(task.getName())) {
      return "Task name '" + task.getName() + "' already exists in " + planLabel(plan)
          + " (existing " + PlanningUtils.describeTask(nameVsTask.get(task.getName())) + ").";
    }
    final String parent = task.getParent();
    if (StringUtils.isNotBlank(parent)) {
      final Task parentTask = nameVsTask.get(parent);
      if (parentTask == null) {
        return PlanningUtils.describeTask(task) + " references missing parent task '" + parent + "'.";
      }
      if (PlanningUtils.isTerminalStatus(PlanningUtils.taskStatus(parentTask))) {
        return "Parent " + PlanningUtils.describeTask(parentTask) + " is " + PlanningUtils.taskStatus(task).getValue()
            + "; cannot add child " + PlanningUtils.describeTask(task) + ".";
      }
    }
    final TaskStatus status = PlanningUtils.taskStatus(task);
    if (status == TaskStatus.IN_PROGRESS) {
      if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
        return planLabel(plan) + " is " + plan.getStatus().getValue()
            + "; must be " + PlanStatus.IN_PROGRESS.getValue() + " before starting tasks.";
      }
      final String ancestorError = validateAncestorsInProgress(task, nameVsTask);
      if (ancestorError != null) {
        return ancestorError;
      }
      final List<Task> inProgressTasks = getInProgressTasks(plan, task, status, true);
      final String lineageError = validateSingleLineage(inProgressTasks, nameVsTask, task);
      if (lineageError != null) {
        return lineageError;
      }
      return validateInProgressOrder(plan, nameVsTask, inProgressTasks);
    }
    return null;
  }

  public String canUpdateTask(
      final Plan plan, final Task task, final TaskStatus newStatus, final String result) {
    if (plan == null || task == null || newStatus == null) {
      return null;
    }
    if (newStatus == TaskStatus.UNKNOWN) {
      return PlanningUtils.describeTask(task) + " has unknown status.";
    }
    if (PlanningUtils.isTerminalStatus(newStatus) && StringUtils.isBlank(result)) {
      return "The 'result' field is required when marking a task as " + newStatus.getValue() + ".";
    }
    if (!PlanningUtils.isTerminalStatus(newStatus) && StringUtils.isNotBlank(result)) {
      return "The 'result' field can only be set when marking a task as done or abandoned.";
    }
    if (PlanningUtils.isTerminalStatus(plan.getStatus())) {
      return planLabel(plan) + " is " + plan.getStatus().getValue()
          + "; must be " + PlanStatus.IN_PROGRESS.getValue() + " to update tasks.";
    }
    final TaskStatus currentStatus = PlanningUtils.taskStatus(task);
    if (currentStatus == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.TODO) {
      return PlanningUtils.describeTask(task) + " cannot move from " + TaskStatus.IN_PROGRESS.getValue()
          + " back to " + TaskStatus.TODO.getValue() + ".";
    }
    if (PlanningUtils.isTerminalStatus(currentStatus) && !PlanningUtils.isTerminalStatus(newStatus)) {
      return PlanningUtils.describeTask(task) + " is " + currentStatus.getValue() + "; cannot reopen it.";
    }
    final Map<String, Task> nameVsTask = CollectionUtils.transformToMap(plan.getTasks(), Task::getName);
    final Map<String, List<Task>> taskTree = PlanningUtils.getTaskVsChildren(plan.getTasks());
    if (newStatus == TaskStatus.IN_PROGRESS) {
      if (plan.getStatus() != null && plan.getStatus() != PlanStatus.IN_PROGRESS) {
        return planLabel(plan) + " is " + plan.getStatus().getValue()
            + "; must be " + PlanStatus.IN_PROGRESS.getValue() + " before starting tasks.";
      }
      final String ancestorError = validateAncestorsInProgress(task, nameVsTask);
      if (ancestorError != null) {
        return ancestorError;
      }
      if (currentStatus != TaskStatus.IN_PROGRESS) {
        final String orderError = validateNextTaskToStart(plan, task);
        if (orderError != null) {
          return orderError;
        }
      }
    }
    if (newStatus == TaskStatus.TODO) {
      final Task descendant = findInProgressDescendant(task, taskTree);
      if (descendant != null) {
        return PlanningUtils.describeTask(task) + " cannot move to todo while child "
            + PlanningUtils.describeTask(descendant) + " is " + TaskStatus.IN_PROGRESS.getValue() + ".";
      }
    }
    if (PlanningUtils.isTerminalStatus(newStatus)) {
      final Task blocking = PlanningUtils.findNonTerminalDescendant(task, taskTree);
      if (blocking != null) {
        return PlanningUtils.describeTask(task) + " cannot be marked " + newStatus.getValue()
            + " while child tasks are not terminal (e.g., "
            + PlanningUtils.describeTask(blocking) + " is " + PlanningUtils.taskStatus(task).getValue() + ").";
      }
      if (StringUtils.isBlank(result)) {
        return PlanningUtils.describeTask(task) + " is being marked " + newStatus.getValue() + " but is missing a result.";
      }
    }
    final List<Task> inProgressTasks = getInProgressTasks(plan, task, newStatus, false);
    final String lineageError = validateSingleLineage(inProgressTasks, nameVsTask, task);
    if (lineageError != null) {
      return lineageError;
    }
    return validateInProgressOrder(plan, nameVsTask, inProgressTasks);
  }

    private static String validateAncestorsInProgress(
      final Task task, final Map<String, Task> nameVsTask) {
    String parent = task.getParent();
    while (StringUtils.isNotBlank(parent)) {
      final Task parentTask = nameVsTask.get(parent);
      if (parentTask == null) {
        return PlanningUtils.describeTask(task) + " references missing parent task '" + parent + "'.";
      }
      if (PlanningUtils.taskStatus(parentTask) != TaskStatus.IN_PROGRESS) {
        return "Parent " + PlanningUtils.describeTask(parentTask) + " must be " + TaskStatus.IN_PROGRESS.getValue()
            + " before starting child " + PlanningUtils.describeTask(task) + ".";
      }
      parent = parentTask.getParent();
    }
    return null;
  }

  private static Task findInProgressDescendant(
      final Task task, final Map<String, List<Task>> taskTree) {
    for (final Task child : CollectionUtils.nullSafeList(taskTree.get(task.getName()))) {
      if (PlanningUtils.taskStatus(child) == TaskStatus.IN_PROGRESS) {
        return child;
      }
      final Task nested = findInProgressDescendant(child, taskTree);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static String validateNextTaskToStart(final Plan plan, final Task task) {
    final Task nextTask = plan.getNextTaskWithStatus(TaskStatus.TODO);
    if (nextTask == null) {
      return "No pending tasks remain to start in " + planLabel(plan) + ".";
    }
    if (nextTask == task || task.getName().equals(nextTask.getName())) {
      return null;
    }
    return "Tasks are started in plan order, so " + PlanningUtils.describeTask(task)
        + " cannot be started yet: the next task to start is " + PlanningUtils.describeTask(nextTask) + ".";
  }

  private static String validateInProgressOrder(
      final Plan plan,
      final Map<String, Task> nameVsTask,
      final List<Task> inProgressTasks) {
    if (CollectionUtils.isEmpty(inProgressTasks)) {
      return null;
    }
    final Task nextTodo = plan.getNextTaskWithStatus(TaskStatus.TODO);
    if (nextTodo == null) {
      return null;
    }
    final Set<String> lineage = getLineageIds(nextTodo, nameVsTask);
    final List<Task> postOrder = plan.getTasks();
    final int nextTodoIndex = postOrder.indexOf(nextTodo);
    for (final Task task : inProgressTasks) {
      // Valid if the task is an ancestor of nextTodo (hierarchical: parent in_progress while its
      // first child subtask is next), or precedes nextTodo in post-order (sequential: this task is
      // the current focus, nextTodo comes after). Invalid only when the task appears after nextTodo
      // in post-order — a TODO task was skipped.
      if (lineage.contains(task.getName())) {
        continue;
      }
      if (nextTodoIndex >= 0 && postOrder.indexOf(task) < nextTodoIndex) {
        continue;
      }
      return PlanningUtils.describeTask(task)
          + " is still in progress, so no other task can be started or completed yet. Carry on"
          + " with its work and then complete it with its result, or complete it now if its work"
          + " is already done. The next task to start after that is "
          + PlanningUtils.describeTask(nextTodo) + " in " + planLabel(plan) + ".";
    }
    return null;
  }

  private static List<Task> getInProgressTasks(
      final Plan plan,
      final Task updatedTask,
      final TaskStatus newStatus,
      final boolean includeMissingTask) {
    final List<Task> inProgress = new ArrayList<>();
    final String updatedName = updatedTask == null ? null : updatedTask.getName();
    boolean updatedFound = false;
    for (final Task task : plan.getTasks()) {
      TaskStatus status = PlanningUtils.taskStatus(task);
      if (task == updatedTask || task.getName().equals(updatedName)) {
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
    final Set<String> lineageIds = getLineageIds(deepest, nameVsTask);
    for (final Task task : inProgressTasks) {
      if (!lineageIds.contains(task.getName())) {
        if (actedOn == null) {
          return "The plan marks " + PlanningUtils.describeTask(task) + " and " + PlanningUtils.describeTask(deepest)
              + " as in progress in different branches, but only one branch can be in progress at"
              + " a time. Mark at most one of them as in progress.";
        }
        final Task blocking = task.getName().equals(actedOn.getName()) ? deepest : task;
        return PlanningUtils.describeTask(actedOn) + " cannot be started or completed while "
            + PlanningUtils.describeTask(blocking) + " is still in progress. Carry on with the work of "
            + PlanningUtils.describeTask(blocking) + " and then complete it with its result, or complete it now"
            + " if its work is already done, and then retry.";
      }
    }
    return null;
  }

  private static Task findDeepestTask(final List<Task> tasks, final Map<String, Task> nameVsTask) {
    Task deepest = null;
    int maxDepth = -1;
    for (final Task task : tasks) {
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
    String parent = task.getParent();
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

  private static Set<String> getLineageIds(final Task task, final Map<String, Task> nameVsTask) {
    final Set<String> lineage = new HashSet<>();
    Task current = task;
    while (current != null) {
      lineage.add(current.getName());
      final String parent = current.getParent();
      if (StringUtils.isBlank(parent)) {
        break;
      }
      current = nameVsTask.get(parent);
    }
    return lineage;
  }
}
