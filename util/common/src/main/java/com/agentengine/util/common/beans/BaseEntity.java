package com.agentengine.util.common.beans;

public abstract class BaseEntity {
  public static final String FIELD_ID = "id";
  public static final String FIELD_CREATED_TIME = "createdTime";
  public static final String FIELD_UPDATED_TIME = "updatedTime";
  private String id;
  private long createdTime;
  private long updatedTime;

  public BaseEntity() {
  }

  public BaseEntity(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public long getCreatedTime() {
    return createdTime;
  }

  public void setCreatedTime(final long createdTime) {
    this.createdTime = createdTime;
  }

  public long getUpdatedTime() {
    return updatedTime;
  }

  public void setUpdatedTime(final long updatedTime) {
    this.updatedTime = updatedTime;
  }
}
