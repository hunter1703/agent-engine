package com.agentengine.agent.infra.tools.beans;

import com.agentengine.util.agents.tools.ToolArg;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Task {

  @ToolArg(description = "Short label identifying this task. Must be unique within the plan.")
  @NotBlank
  private String name;

  @ToolArg(
      description =
          "Name of the parent task ONLY when decomposing a single composite task into internal sub-components. "
              + "DO NOT use 'parent' for sequence, prerequisites, or dependencies. "
              + "Sequential phases (e.g. Phase 1, Phase 2, Synthesis/Conclusion) must ALL be top-level tasks with parent omitted.",
      optional = true)
  private String parent;

  @ToolArg(
      description = "Concise statement of what this task should produce or achieve when completed.")
  @NotBlank
  private String goal;

  @ToolArg(description = "Current lifecycle status of the task.", optional = true)
  @NotNull
  private TaskStatus status = TaskStatus.TODO;

  @ToolArg(
      description = "The outcome or conclusion recorded when the task was completed or abandoned.",
      optional = true)
  private String result;

  @ToolArg(
      description = "Extended context, notes, or instructions for performing this task.",
      optional = true)
  private String description;

  public Task() {}

  public Task(String name, String goal) {
    this.name = name;
    this.goal = goal;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
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
