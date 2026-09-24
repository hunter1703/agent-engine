package com.agentengine.agent.api.model;

import java.util.List;

public record UserMessage(
    List<MessagePart> parts, ResourceGrants grants, List<AgentFileDetails> attachments) {

  public UserMessage(final List<MessagePart> parts, final ResourceGrants grants) {
    this(parts, grants, grants.knowledgeFiles());
  }

  public static UserMessage ofText(final String text) {
    return new UserMessage(List.of(new MessagePart.TextPart(text)), ResourceGrants.EMPTY);
  }
}
