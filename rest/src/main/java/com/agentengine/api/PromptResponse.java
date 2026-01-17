package com.agentengine.api;

import java.util.List;

public class PromptResponse {
  private String sessionId;
  private List<MessageDto> messages;

  public PromptResponse() {}

  public PromptResponse(final String sessionId, final List<MessageDto> messages) {
    this.sessionId = sessionId;
    this.messages = messages;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public List<MessageDto> getMessages() {
    return messages;
  }

  public void setMessages(final List<MessageDto> messages) {
    this.messages = messages;
  }
}
