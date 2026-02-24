package com.agentengine.engine.tools.planning.beans;

import com.agentengine.engine.api.tools.annotations.ToolSchema;
import java.util.UUID;

public class Task {
  @ToolSchema(
      description = "Unique identifier for the task",
      optional = true)
  private String taskId = UUID.randomUUID().toString();

  @ToolSchema(
      name = "parent_id",
      description = "Parent task id for creating a hierarchy between tasks.",
      optional = true)
  private String parentId;

  @ToolSchema(description = "Short name for the task.")
  private String name;

  @ToolSchema(description = "The goal or expected result of this task.")
  private String goal;

  @ToolSchema(description = "Current status of the task.", optional = true)
  private TaskStatus status = TaskStatus.TODO;

  @ToolSchema(description = "Actual result of the task once completed.", optional = true)
  private String result;

  @ToolSchema(description = "Detailed description of the task.", optional = true)
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
