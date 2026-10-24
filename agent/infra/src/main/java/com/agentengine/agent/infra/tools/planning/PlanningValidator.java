package com.agentengine.agent.infra.tools.planning;

import static com.agentengine.agent.infra.tools.planning.PlanningUtils.describeTask;
import static com.agentengine.agent.infra.tools.planning.PlanningUtils.planLabel;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.validation.ValidationCollector;
import com.agentengine.util.common.validation.Validator;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks what a model can get wrong: the plan it describes, the task it adds and the task it
 * completes. Everything else about a plan's state (which task is in progress, what each status
 * allows) is set by the planning code itself, so it is not checked.
 */
@Singleton
public class PlanningValidator implements Validator<Plan> {

  /** Every problem with the plan described by {@code title}, {@code goal} and {@code tasks}. */
  public List<String> errors(
      final String title, final String goal, final List<Task> tasks) {
    final ValidationCollector errors = new ValidationCollector();
    check(title, goal, tasks, errors);
    return errors.errors();
  }

  /** Whether the plan, if it has one, lets the model give its final answer. */
  public boolean allowsFinalAnswer(final Plan plan) {
    return plan == null || PlanningUtils.isTerminalStatus(plan.getStatus());
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
    check(plan.getTitle(), plan.getGoal(), plan.getTasks(), errors);
  }

  /** Why {@code task} cannot be added to {@code plan}, or null when it can. */
  public String canAddTask(final Plan plan, final Task task) {
    if (plan.getStatus() != PlanStatus.IN_PROGRESS) {
      return planLabel(plan)
          + " is "
          + plan.getStatus().getValue()
          + "; must be "
          + PlanStatus.IN_PROGRESS.getValue()
          + " to add tasks.";
    }
    final Task sameName = plan.getTask(task.getName());
    if (sameName != null) {
      return "Task name '"
          + task.getName()
          + "' already exists in "
          + planLabel(plan)
          + " (existing "
          + describeTask(sameName)
          + ").";
    }
    if (StringUtils.isNotBlank(task.getParent())) {
      final Task parentTask = plan.getTask(task.getParent());
      if (parentTask == null) {
        return describeTask(task) + " references missing parent task '" + task.getParent() + "'.";
      }
      if (PlanningUtils.isTerminalStatus(PlanningUtils.taskStatus(parentTask))) {
        return "Parent "
            + describeTask(parentTask)
            + " is "
            + PlanningUtils.taskStatus(parentTask).getValue()
            + "; cannot add child "
            + describeTask(task)
            + ".";
      }
    }
    return null;
  }

  /**
   * Why {@code task} cannot be given the terminal status {@code newStatus} in {@code plan}, or null
   * when it can. Completing needs the task's parts completed first; abandoning takes its open parts
   * with it.
   */
  public String canUpdateTask(
      final Plan plan, final Task task, final TaskStatus newStatus) {
    if (PlanningUtils.isTerminalStatus(plan.getStatus())) {
      return planLabel(plan)
          + " is "
          + plan.getStatus().getValue()
          + "; must be "
          + PlanStatus.IN_PROGRESS.getValue()
          + " to update tasks.";
    }
    final TaskStatus currentStatus = PlanningUtils.taskStatus(task);
    if (PlanningUtils.isTerminalStatus(currentStatus)) {
      return describeTask(task) + " is already " + currentStatus.getValue() + ".";
    }
    if (newStatus == TaskStatus.DONE && currentStatus != TaskStatus.IN_PROGRESS) {
      final Task active = PlanningUtils.activeTask(plan);
      return describeTask(task)
          + " is "
          + currentStatus.getValue()
          + " and cannot be completed yet."
          + (active == null
              ? ""
              : " The task in progress is " + describeTask(active) + "; work on it and complete it first.");
    }
    if (newStatus == TaskStatus.DONE) {
      final Task open =
          PlanningUtils.findNonTerminalDescendant(
              task, PlanningUtils.getTaskVsChildren(plan.getTasks()));
      if (open != null) {
        return describeTask(task)
            + " cannot be completed while its part "
            + describeTask(open)
            + " is "
            + PlanningUtils.taskStatus(open).getValue()
            + ". Complete its parts first, or abandon it to abandon them too.";
      }
    }
    return null;
  }

  private void check(
      final String title,
      final String goal,
      final List<Task> tasks,
      final ValidationCollector errors) {
    if (StringUtils.isBlank(title)) {
      errors.add("Plan has blank title.");
    }
    if (StringUtils.isBlank(goal)) {
      errors.add("Plan has blank goal.");
    }
    if (CollectionUtils.isEmpty(tasks)) {
      errors.add("Plan has no tasks.");
      return;
    }
    final Map<String, Task> loweredNameVsTask = new HashMap<>();
    for (final Task task : tasks) {
      if (StringUtils.isBlank(task.getName())) {
        errors.add("Tasks cannot have blank title.");
        continue;
      }
      if (StringUtils.isBlank(task.getGoal())) {
        errors.add(describeTask(task) + " has blank goal.");
      }
      if (loweredNameVsTask.put(lowered(task.getName()), task) != null) {
        errors.add("Task name '" + task.getName() + "' is used more than once.");
      }
    }
    for (final Task task : tasks) {
      final String parent = task.getParent();
      if (StringUtils.isNotBlank(parent) && !loweredNameVsTask.containsKey(lowered(parent))) {
        errors.add(describeTask(task) + " references missing parent task '" + parent + "'.");
      } else if (StringUtils.isNotBlank(parent) && inParentLoop(task, loweredNameVsTask)) {
        errors.add(describeTask(task) + " is part of a loop of parents.");
      }
    }
  }

  private static boolean inParentLoop(final Task task, final Map<String, Task> loweredNameVsTask) {
    final Set<String> visited = new HashSet<>();
    Task current = task;
    while (current != null && StringUtils.isNotBlank(current.getParent())) {
      if (!visited.add(lowered(current.getName()))) {
        return true;
      }
      current = loweredNameVsTask.get(lowered(current.getParent()));
    }
    return false;
  }

  private static String lowered(final String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
