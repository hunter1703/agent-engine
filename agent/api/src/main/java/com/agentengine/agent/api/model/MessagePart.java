package com.agentengine.agent.api.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = MessagePart.TextPart.class, name = "text")})
public interface MessagePart {

  record TextPart(String text) implements MessagePart {}
}
