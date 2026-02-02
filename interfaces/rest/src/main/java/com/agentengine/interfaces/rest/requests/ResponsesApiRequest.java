package com.agentengine.interfaces.rest.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request class specifically shaped for the Responses API specification
 * Compatible with Codex CLI expectations
 */
public class ResponsesApiRequest {

  @JsonProperty("model")
  private String model;

  @JsonProperty("messages")
  private Message[] messages;

  @JsonProperty("thread_id")
  private String threadId;

  @JsonProperty("run_id")
  private String runId;

  @JsonProperty("instructions")
  private String instructions;

  @JsonProperty("metadata")
  private Map<String, Object> metadata;

  // Constructors
  public ResponsesApiRequest() {
  }

  public ResponsesApiRequest(String model, Message[] messages) {
    this.model = model;
    this.messages = messages;
  }

  // Getters and setters
  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Message[] getMessages() {
    return messages;
  }

  public void setMessages(Message[] messages) {
    this.messages = messages;
  }

  public String getThreadId() {
    return threadId;
  }

  public void setThreadId(String threadId) {
    this.threadId = threadId;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(String runId) {
    this.runId = runId;
  }

  public String getInstructions() {
    return instructions;
  }

  public void setInstructions(String instructions) {
    this.instructions = instructions;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  /**
   * Inner class representing a message in the conversation
   */
  public static class Message {
    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;

    public Message() {
    }

    public Message(String role, String content) {
      this.role = role;
      this.content = content;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }
  }
}