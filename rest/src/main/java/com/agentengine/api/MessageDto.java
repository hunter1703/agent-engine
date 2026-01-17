package com.agentengine.api;

public class MessageDto {
  private String role;
  private String content;

  public MessageDto() {}

  public MessageDto(final String role, final String content) {
    this.role = role;
    this.content = content;
  }

  public String getRole() {
    return role;
  }

  public void setRole(final String role) {
    this.role = role;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }
}
