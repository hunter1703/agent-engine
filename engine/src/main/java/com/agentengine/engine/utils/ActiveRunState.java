package com.agentengine.engine.utils;

import com.agentengine.util.common.StringUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ActiveRunState {
  private String runId;
  private String rootAgentId;
  private String currentInvocationId;
  private String status = RunStatus.UNKNOWN.name();
  private Long startedAt;
  private Long updatedAt;
  private Map<String, RunState> agentStates = new ConcurrentHashMap<>();

  public ActiveRunState() {}

  public String runId() {
    return runId;
  }

  public String rootAgentId() {
    return rootAgentId;
  }

  public String currentInvocationId() {
    return currentInvocationId;
  }

  public String status() {
    return status;
  }

  public RunStatus statusEnum() {
    return RunStatus.valueOfOrDefault(status);
  }

  public Long startedAt() {
    return startedAt;
  }

  public Long updatedAt() {
    return updatedAt;
  }

  public Map<String, RunState> agentStates() {
    return agentStates == null ? Map.of() : Map.copyOf(agentStates);
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(final String runId) {
    this.runId = StringUtils.isBlank(runId) ? null : runId.trim();
  }

  public String getRootAgentId() {
    return rootAgentId;
  }

  public void setRootAgentId(final String rootAgentId) {
    this.rootAgentId = StringUtils.isBlank(rootAgentId) ? null : rootAgentId.trim();
  }

  public String getCurrentInvocationId() {
    return currentInvocationId;
  }

  public void setCurrentInvocationId(final String currentInvocationId) {
    this.currentInvocationId =
        StringUtils.isBlank(currentInvocationId) ? null : currentInvocationId.trim();
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = RunStatus.valueOfOrDefault(status).name();
  }

  public Long getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(final Long startedAt) {
    this.startedAt = startedAt;
  }

  public Long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(final Long updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Map<String, RunState> getAgentStates() {
    if (agentStates == null) {
      agentStates = new ConcurrentHashMap<>();
    }
    return agentStates;
  }

  public void setAgentStates(final Map<String, RunState> agentStates) {
    this.agentStates =
        agentStates == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(agentStates);
  }

  public boolean isTerminal() {
    return statusEnum().isTerminal();
  }
}
