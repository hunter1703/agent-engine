package com.agentengine.interfaces.rest.requests;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ResponsesApiRequest {

  private String model;
  private List<InputMessage> input;

  @JsonProperty("session_id")
  @JsonAlias("prompt_cache_key")
  private String sessionId;

  public ResponsesApiRequest() {}

  public ResponsesApiRequest(String model, List<InputMessage> input) {
    this.model = model;
    this.input = input;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public List<InputMessage> getInput() {
    return input;
  }

  public void setInput(List<InputMessage> input) {
    this.input = input;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public static class InputMessage {
    private String type;
    private String role;
    private List<ContentPart> content;

    public InputMessage() {}

    public InputMessage(String type, String role, List<ContentPart> content) {
      this.type = type;
      this.role = role;
      this.content = content;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public List<ContentPart> getContent() {
      return content;
    }

    public void setContent(List<ContentPart> content) {
      this.content = content;
    }
  }

  public static class ContentPart {
    private String type;
    private String text;

    public ContentPart() {}

    public ContentPart(String type, String text) {
      this.type = type;
      this.text = text;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }
  }
}
