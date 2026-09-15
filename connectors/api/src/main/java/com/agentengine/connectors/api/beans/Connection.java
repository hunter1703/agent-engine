package com.agentengine.connectors.api.beans;

import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.Secure;
import com.agentengine.util.common.beans.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import org.bson.codecs.pojo.annotations.BsonIgnore;

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

  @BsonIgnore
  public Map<String, Object> getCredentials() {
    return credentials;
  }

  @BsonIgnore
  public void setCredentials(Map<String, Object> credentials) {
    this.credentials = credentials;
  }

  @Secure
  @JsonIgnore
  public String getCredentialsStr() {
    if (credentials == null) {
      return null;
    }
    try {
      return JsonUtils.toJson(credentials);
    } catch (Exception e) {
      return null;
    }
  }

  @JsonIgnore
  public void setCredentialsStr(String credentialsStr) {
    if (credentialsStr == null) {
      this.credentials = null;
      return;
    }
    try {
      this.credentials =
          JsonUtils.fromJson(credentialsStr, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      this.credentials = null;
    }
  }

  public Long getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Long expiresAt) {
    this.expiresAt = expiresAt;
  }
}
