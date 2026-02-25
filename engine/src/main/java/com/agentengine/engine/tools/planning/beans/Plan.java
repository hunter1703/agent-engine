package com.agentengine.engine.tools.planning.beans;

import com.agentengine.engine.tools.planning.PlanningValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Plan {
  private String planId = UUID.randomUUID().toString();
  private String title;
  private String goal;
  private List<Task> tasks = new ArrayList<>();
  private PlanStatus status = PlanStatus.IN_PROGRESS;
  private String result;

  public Plan() {}

  public Plan(String title, String goal) {
    this.title = title;
    this.goal = goal;
  }

  public Plan(String title, String goal, List<Task> tasks) {
    this.title = title;
    this.goal = goal;
    this.tasks = tasks;
  }

  public String getPlanId() {
    return planId;
  }

  public void setPlanId(String planId) {
    this.planId = planId;
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

  public void setTasks(List<Task> tasks) {
    this.tasks = tasks;
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

  public void finish(PlanStatus status, String result) {
    this.status = status;
    this.result = result;
  }

  public String validate() {
    return PlanningValidator.validatePlan(this);
  }

  public String canAddTask(final Task task) {
    return PlanningValidator.canAddTask(this, task);
  }

  public String canUpdateTask(final Task task, final TaskStatus newStatus, final String result) {
    return PlanningValidator.canUpdateTask(this, task, newStatus, result);
  }

  public String canFinish(final PlanStatus newStatus, final String result) {
    return PlanningValidator.canFinishPlan(this, newStatus, result);
  }
}
