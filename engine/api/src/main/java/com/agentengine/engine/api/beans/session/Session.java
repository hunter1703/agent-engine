package com.agentengine.engine.api.beans.session;

import java.util.ArrayList;
import java.util.List;

public class Session {
  private String id;
  private String agentId;
  private String runId;
  private boolean awaitingClarification;
  private ToolCall pendingClarification;
  private boolean tasksGenerated;
  private List<PlanItem> pendingPlan = new ArrayList<>();
  private PlanItem activePlanItem;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(final String runId) {
    this.runId = runId;
  }

  public boolean isAwaitingClarification() {
    return awaitingClarification;
  }

  public void setAwaitingClarification(final boolean awaitingClarification) {
    this.awaitingClarification = awaitingClarification;
  }

  public ToolCall getPendingClarification() {
    return pendingClarification;
  }

  public void setPendingClarification(final ToolCall pendingClarification) {
    this.pendingClarification = pendingClarification;
  }

  public boolean isTasksGenerated() {
    return tasksGenerated;
  }

  public void setTasksGenerated(final boolean tasksGenerated) {
    this.tasksGenerated = tasksGenerated;
  }

  public List<PlanItem> getPendingPlan() {
    return pendingPlan == null ? List.of() : new ArrayList<>(pendingPlan);
  }

  public void setPendingPlan(final List<PlanItem> pendingPlan) {
    this.pendingPlan = pendingPlan == null ? new ArrayList<>() : new ArrayList<>(pendingPlan);
  }

  public PlanItem getActivePlanItem() {
    return activePlanItem;
  }

  public void setActivePlanItem(final PlanItem activePlanItem) {
    this.activePlanItem = activePlanItem;
  }
}
