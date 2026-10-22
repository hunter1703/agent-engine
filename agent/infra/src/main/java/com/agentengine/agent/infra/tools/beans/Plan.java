package com.agentengine.agent.infra.tools.beans;

import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Plan {
  @NotBlank private String title;
  @NotBlank private String goal;
  @NotEmpty private List<Task> tasks;
  @NotNull private PlanStatus status = PlanStatus.IN_PROGRESS;
  private String result;

  public Plan() {}

  public Plan(Plan plan) {
    this(
        plan.title,
        plan.goal,
        CollectionUtils.nullSafeList(plan.tasks).stream().map(Task::new).toList(),
        plan.status,
        plan.result);
  }

  public Plan(String title, String goal, List<Task> tasks) {
    this(title, goal, tasks, PlanStatus.IN_PROGRESS, null);
  }

  public Plan(String title, String goal, List<Task> tasks, PlanStatus status, String result) {
    this.title = title;
    this.goal = goal;
    this.tasks = buildPostOrder(tasks);
    this.status = status;
    this.result = result;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getGoal() {
    return goal;
  }

  public void setGoal(String goal) {
    this.goal = goal;
  }

  public List<Task> getTasks() {
    return tasks;
  }

  public void setTasks(@NotEmpty List<Task> tasks) {
    this.tasks = tasks;
  }

  public Task getTask(final String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    final String wanted = name.trim();
    return tasks.stream()
        .filter(task -> task.getName() != null && task.getName().trim().equalsIgnoreCase(wanted))
        .findFirst()
        .orElse(null);
  }

  /** This plan with {@code task} added, in its place in the task order. */
  public Plan addTask(final Task task) {
    final List<Task> updatedTasks = CollectionUtils.nullSafeMutableList(tasks);
    updatedTasks.add(task);
    setTasks(buildPostOrder(updatedTasks));
    return this;
  }

  public PlanStatus getStatus() {
    return status;
  }

  public void setStatus(PlanStatus status) {
    this.status = status;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  /** This plan abandoned, with each of its open tasks, for {@code reason}. */
  public Plan abandon(final String reason) {
    for (final Task task : tasks) {
      if (!task.getStatus().isTerminal()) {
        task.setStatus(TaskStatus.ABANDONED);
        task.setResult("Abandoned with the plan.");
      }
    }
    finish(PlanStatus.ABANDONED, reason);
    return this;
  }

  public void finish(PlanStatus status, String result) {
    this.status = status;
    this.result = result;
  }

  public Task getNextTaskWithStatus(final TaskStatus status) {
    for (final Task task : getTasks()) {
      if (task.getStatus() == status) {
        return task;
      }
    }
    return null;
  }

  public boolean allTasksTerminal() {
    for (final Task task : getTasks()) {
      if (!task.getStatus().isTerminal()) {
        return false;
      }
    }
    return true;
  }

  private List<Task> buildPostOrder(final List<Task> tasks) {
    final Map<String, List<Task>> parentVsChildren = PlanningUtils.getTaskVsChildren(tasks);
    final List<Task> ordered = new ArrayList<>(tasks.size());
    for (final Task task : tasks) {
      if (StringUtils.isBlank(task.getParent())) {
        ordered.addAll(traverse(task, parentVsChildren));
      }
    }
    return ordered;
  }

  private List<Task> traverse(final Task task, final Map<String, List<Task>> parentVsChildren) {
    final List<Task> ordered = new ArrayList<>();
    for (final Task child : parentVsChildren.getOrDefault(task.getName(), List.of())) {
      ordered.addAll(traverse(child, parentVsChildren));
    }
    ordered.add(task);
    return ordered;
  }

}
