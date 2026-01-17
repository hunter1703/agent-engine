package com.agentengine.client;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonAlias;

public class AgentRequest {
  private String type;
  @JSONField(alternateNames = {"agent_name"})
  private String agentName;
  @JSONField(alternateNames = {"agent_config_path"})
  private String agentConfigPath;
  private String sessionId;

  @JSONField(alternateNames = {"user_message"})
  private String message;

  public String getType() {
    return type;
  }

  public void setType(final String type) {
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
