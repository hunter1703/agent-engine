package com.agentengine.interfaces.rest.models;

import java.time.Instant;

/**
 * Represents a model in the OpenAI-compatible format
 */
public class ModelInfo {

  private String id;
  private String object = "model";
  private long created;
  private String ownedBy;

  public ModelInfo() {
    this.created = Instant.now().getEpochSecond();
  }

  public ModelInfo(String id, String ownedBy) {
    this.id = id;
    this.ownedBy = ownedBy;
    this.created = Instant.now().getEpochSecond();
    this.object = "model";
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getObject() {
    return object;
  }

  public void setObject(String object) {
    this.object = object;
  }

  public long getCreated() {
    return created;
  }

  public void setCreated(long created) {
    this.created = created;
  }

  public String getOwnedBy() {
    return ownedBy;
  }

  public void setOwnedBy(String ownedBy) {
    this.ownedBy = ownedBy;
  }
}