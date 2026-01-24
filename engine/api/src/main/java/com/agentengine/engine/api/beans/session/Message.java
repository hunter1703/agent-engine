package com.agentengine.engine.api.beans.session;

import java.util.List;

public class Message {
  private String id;
  private String runId;
  private final Role role;
  private final String content;
  private final String thoughts;
  private final List<ToolCall> toolCalls;

  public Message(final Role role, final String content, final String thoughts, final List<ToolCall> toolCalls) {
    this.role = role;
    this.content = content;
    this.thoughts = thoughts;
    this.toolCalls = toolCalls;
  }

  public Message(final Message other, final String content) {
    this.id = other.id;
    this.runId = other.runId;
    this.role = other.role;
    this.content = content;
    this.thoughts = other.thoughts;
    this.toolCalls = other.toolCalls;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setRunId(final String runId) {
    this.runId = runId;
  }

  public String getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public Role getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public String getThoughts() {
    return thoughts;
  }

  public List<ToolCall> getToolCalls() {
    return toolCalls;
  }

  public static Message system(final String content) {
    return new Message(Role.SYSTEM, content, null, null);
  }

  public static Message user(final String content) {
    return new Message(Role.USER, content, null, null);
  }

  public static Message assistant(final String content) {
    return new Message(Role.ASSISTANT, content, null, null);
  }

  public static Message assistant(final String content, List<ToolCall> toolCalls) {
    return new Message(Role.ASSISTANT, content, null, toolCalls);
  }
}
