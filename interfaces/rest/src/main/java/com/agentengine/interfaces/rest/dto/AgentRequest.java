package com.agentengine.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public class AgentRequest {
  @NotBlank(message = "types is required")
  private String type;

  @NotBlank(message = "agentId is required")
  private String agentId;

  private String sessionId;

  @NotBlank(message = "message is required")
  private String message;

  public AgentRequest() {
  }

  private AgentRequest(final String type, final String agentId, final String sessionId, final String message) {
    this.type = type;
    this.agentId = agentId;
    this.sessionId = sessionId;
    this.message = message;
  }

  public AgentRequest withSessionId(final String sessionId) {
    return new AgentRequest(this.type, this.agentId, sessionId, this.message);
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
    UNKNOWN, STREAM_AGUI_EVENTS, RESUME_SESSION;

    public static RequestType valueOfOrDefault(final String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      try {
        return RequestType.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }
}
