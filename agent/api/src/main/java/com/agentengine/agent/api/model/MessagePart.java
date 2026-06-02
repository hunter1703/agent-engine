package com.agentengine.agent.api.model;

import com.agentengine.util.common.beans.FileDetails;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MessagePart.TextPart.class, name = "text"),
    @JsonSubTypes.Type(value = MessagePart.FilePart.class, name = "document")
})
public interface MessagePart {

    record TextPart(String text) implements MessagePart {}

    record FilePart(FileDetails fileDetails) implements MessagePart {}
}
