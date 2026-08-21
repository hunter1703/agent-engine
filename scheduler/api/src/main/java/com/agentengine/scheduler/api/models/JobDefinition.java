package com.agentengine.scheduler.api.models;

import com.agentengine.util.common.beans.BaseEntity;
import java.util.List;
import java.util.Map;

public class JobDefinition extends BaseEntity {

  public static final String FIELD_JOB_CLASS_NAME = "jobClassName";

  private String jobClassName;
  private List<String> jobTags;
  private String cronSchedule;
  private Map<String, Object> payload;

  public String getJobClassName() {
    return jobClassName;
  }

  public void setJobClassName(final String jobClassName) {
    this.jobClassName = jobClassName;
  }

  public List<String> getJobTags() {
    return jobTags;
  }

  public void setJobTags(final List<String> jobTags) {
    this.jobTags = jobTags;
  }

  public String getCronSchedule() {
    return cronSchedule;
  }

  public void setCronSchedule(final String cronSchedule) {
    this.cronSchedule = cronSchedule;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(final Map<String, Object> payload) {
    this.payload = payload;
  }
}
