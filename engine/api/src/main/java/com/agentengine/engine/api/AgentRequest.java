package com.agentengine.engine.api;

import jakarta.validation.constraints.NotBlank;

public class AgentRequest {
  @NotBlank(message = "types is required")
  private String type;

  @NotBlank(message = "agentId is required")
  private String agentId;
  private String agentConfigPath;

  private String sessionId;

  @NotBlank(message = "message is required")
  private String message;

  public AgentRequest() {
  }

  private AgentRequest(final String type, final String agentId, final String agentConfigPath, final String sessionId,
      final String message) {
    this.type = type;
    this.agentId = agentId;
    this.agentConfigPath = agentConfigPath;
    this.sessionId = sessionId;
    this.message = message;
  }

  public AgentRequest withSessionId(final String sessionId) {
    return new AgentRequest(this.type, this.agentId, this.agentConfigPath, sessionId, this.message);
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
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
    INVOKE_AGENT, STREAM_AGUI_EVENTS, STREAM_RESPONSES, BUILD_EVENT, STOP_AGENT
  }
}
