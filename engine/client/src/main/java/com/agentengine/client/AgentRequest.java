package com.agentengine.client;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonAlias;

public class AgentRequest {
  private String id;
  private RequestType type;
  private String agentName;
  private String agentConfigPath;
  private String sessionId;

  @JsonAlias("user_message")
  @JSONField(alternateNames = {"user_message"})
  private String message;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public RequestType getType() {
    return type;
  }

  public void setType(final RequestType type) {
    this.type = type;
  }

  public String getAgentName() {
    return agentName;
  }

  public void setAgentName(final String agentName) {
    this.agentName = agentName;
  }

  public String getAgentConfigPath() {
    return agentConfigPath;
  }

  public void setAgentConfigPath(final String agentConfigPath) {
    this.agentConfigPath = agentConfigPath;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public enum RequestType {
    INVOKE_AGENT,
    BUILD_PROMPT,
    BUILD_EVENT,
    STOP_AGENT
  }
}
