package com.agentengine.runtime.api.model;

import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MessagePart.TextPart.class, name = "text"),
    @JsonSubTypes.Type(value = MessagePart.FilePart.class, name = "file")
})
public interface MessagePart {

    record TextPart(String text) implements MessagePart {}

    record FilePart(FileDetails fileDetails) implements MessagePart {
        public FilePart resolved(CloudStorageService cloudStorageService) {
            final FileDetails resolved = fileDetails.resolved(cloudStorageService);
            return new FilePart(resolved);
        }
    }
}
