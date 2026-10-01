package com.agentengine.util.agents.beans;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import jakarta.validation.constraints.NotBlank;

/**
 * A recurring run of an agent: {@code message} is sent to it on every firing of {@code
 * cronSchedule}. The scheduler job that fires it shares its id.
 */
@Index(name = "agent_schedule_agent_idx", def = "{'agentId': 1}")
@Permissioned(assetClass = AssetClass.AGENT_SCHEDULE)
public class AgentSchedule extends BaseEntity {

  public static final String FIELD_AGENT_ID = "agentId";

  @NotBlank private String agentId;

  @NotBlank private String cronSchedule;

  @NotBlank private String message;

  /**
   * Whether every firing after the first continues the session the first firing started, instead of
   * each firing getting its own fresh one.
   */
  private boolean singletonSession;

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getCronSchedule() {
    return cronSchedule;
  }

  public void setCronSchedule(final String cronSchedule) {
    this.cronSchedule = cronSchedule;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public boolean isSingletonSession() {
    return singletonSession;
  }

  public void setSingletonSession(final boolean singletonSession) {
    this.singletonSession = singletonSession;
  }
}
