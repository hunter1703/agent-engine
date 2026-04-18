package com.agentengine.runtime.api.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MessagePart.TextPart.class, name = "text"),
    @JsonSubTypes.Type(value = MessagePart.ImagePart.class, name = "image")
})
public interface MessagePart {

    record TextPart(String text) implements MessagePart {}

    record ImagePart(String base64, String mimeType) implements MessagePart {}
}
