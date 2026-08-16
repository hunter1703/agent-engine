package com.agentengine.agent.infra.tools.beans;

import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Task {
  @ToolSchema(
      name = "task_id",
      description =
          "Auto-generated unique identifier for this task. Set by the system; do not provide when creating tasks.",
      optional = true)
  private String taskId = UUID.randomUUID().toString();

  @ToolSchema(
      name = "parent_id",
      description =
          "ID (task_id) of an existing task that this task is a child of. Establishes both hierarchy and "
              + "execution ordering: the parent must be IN_PROGRESS before a child can be started, and "
              + "the parent cannot be completed until all children are in a terminal state. Tasks are "
              + "executed depth-first (parent before children; children before siblings). "
              + "Omit for top-level tasks.",
      optional = true)
  private String parentId;

  @ToolSchema(description = "Short label identifying this task. Should be unique within the plan.")
  private String name;

  @ToolSchema(
      description = "Concise statement of what this task should produce or achieve when completed.")
  private String goal;

  @ToolSchema(
      description = "Current lifecycle status of the task: TODO, IN_PROGRESS, DONE, or ABANDONED.",
      optional = true)
  private TaskStatus status = TaskStatus.TODO;

  @ToolSchema(
      description = "The outcome or conclusion recorded when the task was completed or abandoned.",
      optional = true)
  private String result;

  @ToolSchema(
      description = "Extended context, notes, or instructions for performing this task.",
      optional = true)
  private String description;

  public Task() {}

  public Task(String name, String goal) {
    this.name = name;
    this.goal = goal;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGoal() {
    return goal;
  }

  public void setGoal(String goal) {
    this.goal = goal;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public void setStatus(TaskStatus status) {
    this.status = status;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
