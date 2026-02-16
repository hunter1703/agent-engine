package com.agentengine.engine.api.beans;

public abstract class BaseEntity {
  public static final String FIELD_UPDATED_TIME = "updatedTime";
  private String id;
  private Long createdTime;
  private Long updatedTime;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Long getCreatedTime() {
    return createdTime;
  }

  public void setCreatedTime(final Long createdTime) {
    this.createdTime = createdTime;
  }

  public Long getUpdatedTime() {
    return updatedTime;
  }

  public void setUpdatedTime(final Long updatedTime) {
    this.updatedTime = updatedTime;
  }
}
