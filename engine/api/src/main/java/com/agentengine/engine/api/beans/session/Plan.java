package com.agentengine.engine.api.beans.session;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class Plan {
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private String id = UUID.randomUUID().toString();
  private String name;
  private String description;
  private String expectedOutcome;
  private List<Plan> subtasks;
  private String createdAt;
  private PlanStatus status = PlanStatus.TODO;
  private String finishedAt;
  private String outcome;

  public Plan() {
    this.createdAt = ZonedDateTime.now().format(FORMATTER);
  }

  public Plan(String name, String description, String expectedOutcome, List<Plan> subtasks) {
    this();
    this.name = name;
    this.description = description;
    this.expectedOutcome = expectedOutcome;
    this.subtasks = subtasks;
  }

  public Plan(String name, String description, String expectedOutcome) {
    this();
    this.name = name;
    this.description = description;
    this.expectedOutcome = expectedOutcome;
  }

  public void finish(String outcome) {
    this.status = PlanStatus.DONE;
    this.outcome = outcome;
    this.finishedAt = ZonedDateTime.now().format(FORMATTER);
  }

  public void finish(PlanStatus status, String outcome) {
    this.status = status;
    this.outcome = outcome;
    this.finishedAt = ZonedDateTime.now().format(FORMATTER);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getExpectedOutcome() {
    return expectedOutcome;
  }

  public void setExpectedOutcome(String expectedOutcome) {
    this.expectedOutcome = expectedOutcome;
  }

  public List<Plan> getSubtasks() {
    return subtasks;
  }

  public void setSubtasks(List<Plan> subtasks) {
    this.subtasks = subtasks;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }

  public PlanStatus getStatus() {
    return status;
  }

  public void setStatus(PlanStatus status) {
    this.status = status;
  }

  public String getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(String finishedAt) {
    this.finishedAt = finishedAt;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }
}
