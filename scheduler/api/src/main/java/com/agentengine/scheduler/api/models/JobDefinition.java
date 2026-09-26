package com.agentengine.scheduler.api.models;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.context.UserContext;
import java.util.Map;

@Index(name = "job_class_name_idx", def = "{'jobClassName': 1}")
public class JobDefinition extends BaseEntity {

  public static final String FIELD_JOB_CLASS_NAME = "jobClassName";
  public static final String FIELD_USER_CONTEXT = "userContext";

  private UserContext userContext;
  private String jobClassName;

  /** Exactly one of this and {@link #runAt} must be set — see their own docs. */
  private String cronSchedule;

  /**
   * Epoch millis for a job that fires exactly once, at this instant, instead of on a recurring cron
   * schedule — mutually exclusive with {@link #cronSchedule}.
   */
  private Long runAt;

  private Map<String, Object> payload;

  public UserContext getUserContext() {
    return userContext;
  }

  public void setUserContext(final UserContext userContext) {
    this.userContext = userContext;
  }

  public String getJobClassName() {
    return jobClassName;
  }

  public void setJobClassName(final String jobClassName) {
    this.jobClassName = jobClassName;
  }

  public String getCronSchedule() {
    return cronSchedule;
  }

  public void setCronSchedule(final String cronSchedule) {
    this.cronSchedule = cronSchedule;
  }

  public Long getRunAt() {
    return runAt;
  }

  public void setRunAt(final Long runAt) {
    this.runAt = runAt;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(final Map<String, Object> payload) {
    this.payload = payload;
  }
}
