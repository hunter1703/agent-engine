package com.agentengine.connectors.api.beans;

import com.agentengine.util.common.beans.BaseEntity;
import java.util.Map;

public class Connection extends BaseEntity {
  public static final String FIELD_APP_NAME = "appName";

  private String name;
  private String appName;
  private Map<String, Object> inputs;
  private Map<String, Object> credentials;
  private Long expiresAt;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public Map<String, Object> getInputs() {
    return inputs;
  }

  public void setInputs(Map<String, Object> inputs) {
    this.inputs = inputs;
  }

  public Map<String, Object> getCredentials() {
    return credentials;
  }

  public void setCredentials(Map<String, Object> credentials) {
    this.credentials = credentials;
  }

  public Long getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Long expiresAt) {
    this.expiresAt = expiresAt;
  }
}
