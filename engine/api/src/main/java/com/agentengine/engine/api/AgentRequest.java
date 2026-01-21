package com.agentengine.engine.api;

/**
 * Request to invoke an agent. For immutable semantics, use
 * {@link #withSessionId(String)} to create modified copies.
 */
public class AgentRequest {
  private String type;
  private String agentName;
  private String agentConfigPath;
  private String sessionId;
  private String message;

  public AgentRequest() {
  }

  private AgentRequest(final String type, final String agentName, final String agentConfigPath, final String sessionId,
      final String message) {
    this.type = type;
    this.agentName = agentName;
    this.agentConfigPath = agentConfigPath;
    this.sessionId = sessionId;
    this.message = message;
  }

  /**
   * Creates a copy of this request with the specified session ID.
   */
  public AgentRequest withSessionId(final String sessionId) {
    return new AgentRequest(this.type, this.agentName, this.agentConfigPath, sessionId, this.message);
  }

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
    INVOKE_AGENT, STREAMING_INVOKE_AGENT, BUILD_PROMPT, BUILD_EVENT, STOP_AGENT
  }
}
