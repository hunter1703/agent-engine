package com.agentengine.scheduler.api.models;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;
import java.util.Map;

@Index(name = "job_class_name_idx", def = "{'jobClassName': 1}")
@Index(name = "job_customer_idx", def = "{'customerId': 1}")
public class JobDefinition extends BaseEntity {

  public static final String FIELD_JOB_CLASS_NAME = "jobClassName";
  private String customerId;

  /** Who the job runs as: the written form of the {@code Caller} that scheduled it. */
  private String caller;

  private String jobClassName;

  /** Exactly one of this and {@link #runAt} must be set — see their own docs. */
  private String cronSchedule;

  /**
   * Epoch millis for a job that fires exactly once, at this instant, instead of on a recurring cron
   * schedule — mutually exclusive with {@link #cronSchedule}.
   */
  private Long runAt;

  private Map<String, Object> payload;

  /** The customer the job was scheduled in, and runs in. */
  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(final String customerId) {
    this.customerId = customerId;
  }

  public String getCaller() {
    return caller;
  }

  public void setCaller(final String caller) {
    this.caller = caller;
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
