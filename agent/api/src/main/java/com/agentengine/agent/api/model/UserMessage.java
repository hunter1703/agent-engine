package com.agentengine.agent.api.model;

import java.util.List;

public record UserMessage(List<MessagePart> parts) {

    public static UserMessage ofText(final String text) {
        return new UserMessage(List.of(new MessagePart.TextPart(text)));
    }
}
