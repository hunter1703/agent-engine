package com.agentengine.engine.message;

import java.util.List;

public class Message {
  private String id;
  private final Role role;
  private final String content;
  private final String thoughts;
  private final List<String> toolRequests;
  private final List<ToolCall> toolCalls;

  public Message(
      final Role role,
      final String content,
      final String thoughts,
      final List<String> toolRequests,
      final List<ToolCall> toolCalls) {
    this.role = role;
    this.content = content;
    this.thoughts = thoughts;
    this.toolRequests = toolRequests;
    this.toolCalls = toolCalls;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
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

  public List<String> getToolRequests() {
    return toolRequests;
  }

  public List<ToolCall> getToolCalls() {
    return toolCalls;
  }

  public static Message system(final String content) {
    return new Message(Role.SYSTEM, content, null, null, null);
  }

  public static Message user(final String content) {
    return new Message(Role.USER, content, null, null, null);
  }

  public static Message tool(final String content) {
    return new Message(Role.TOOL, content, null, null, null);
  }

  public static Message assistant(final String content, final String thoughts) {
    return new Message(Role.ASSISTANT, content, thoughts, null, null);
  }
}

