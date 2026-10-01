package com.agentengine.agent.api.model;

import com.agentengine.util.agents.AgentFileDetails;
import java.util.List;

public record UserMessage(List<MessagePart> parts, List<AgentFileDetails> attachments) {

  public UserMessage(final List<MessagePart> parts) {
    this(parts, List.of());
  }

  public static UserMessage ofText(final String text) {
    return new UserMessage(List.of(new MessagePart.TextPart(text)));
  }
}
