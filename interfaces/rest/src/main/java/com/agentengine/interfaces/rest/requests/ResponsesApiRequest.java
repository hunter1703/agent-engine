package com.agentengine.interfaces.rest.requests;

import java.util.List;

/**
 * Request class specifically shaped for the Responses API specification
 * Compatible with Codex CLI expectations
 */
public class ResponsesApiRequest {

  private String model;
  private List<InputMessage> input;

  // Constructors
  public ResponsesApiRequest() {
  }

  public ResponsesApiRequest(String model, List<InputMessage> input) {
    this.model = model;
    this.input = input;
  }

  // Getters and setters
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

  /**
   * Inner class representing a message in the input array
   */
  public static class InputMessage {
    private String type;
    private String role;
    private List<ContentPart> content;

    public InputMessage() {
    }

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

  /**
   * Inner class representing a content part
   */
  public static class ContentPart {
    private String type;
    private String text;

    public ContentPart() {
    }

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