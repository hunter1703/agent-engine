package com.agentengine.cli.beans;

import com.alibaba.fastjson2.annotation.JSONField;

public class Request {

  private String id;
  private RequestType type;

  @JSONField(name = "user_message")
  private String userMessage;

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

  public String getUserMessage() {
    return userMessage;
  }

  public void setUserMessage(final String userMessage) {
    this.userMessage = userMessage;
  }

  public enum RequestType {
    INVOKE_AGENT,
    BUILD_PROMPT,
    BUILD_EVENT,
    STOP_AGENT
  }
}
