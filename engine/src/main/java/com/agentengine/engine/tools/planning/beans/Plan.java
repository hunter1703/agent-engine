package com.agentengine.engine.tools.planning.beans;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Plan {
  private String id = UUID.randomUUID().toString();
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

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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
}
