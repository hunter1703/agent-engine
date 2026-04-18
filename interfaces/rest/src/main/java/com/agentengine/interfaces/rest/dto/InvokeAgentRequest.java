package com.agentengine.interfaces.rest.dto;

import com.agentengine.runtime.api.model.MessagePart;
import com.agentengine.runtime.api.model.UserMessage;
import com.agentengine.util.common.CollectionUtils;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class InvokeAgentRequest {

    private String sessionId;

    @NotEmpty
    private List<PartDto> parts;

    public InvokeAgentRequest() {}

    public UserMessage getUserMessage() {
        if (CollectionUtils.isNotEmpty(parts)) {
            return new UserMessage(parts.stream().map(PartDto::toMessagePart).toList());
        }
        return null;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public List<PartDto> getParts() {
        return parts;
    }

    public void setParts(final List<PartDto> parts) {
        this.parts = parts;
    }

    public static class PartDto {
        private String type;
        private String text;
        private String base64;
        private String mimeType;

        public PartDto() {}

        public MessagePart toMessagePart() {
            return switch (type) {
                case "text" -> new MessagePart.TextPart(text);
                case "image" -> new MessagePart.ImagePart(base64, mimeType);
                default -> throw new IllegalArgumentException("Unknown part type: " + type);
            };
        }

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }

        public String getBase64() {
            return base64;
        }

        public void setBase64(final String base64) {
            this.base64 = base64;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(final String mimeType) {
            this.mimeType = mimeType;
        }
    }
}
