package com.agentengine.interfaces.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * AG-UI {@code RunAgentInput} — request body for POST /v1/agent/{agentId}/invoke.
 *
 * <pre>{@code
 * {
 *   "threadId": "frontend-uuid",
 *   "runId":    "run-uuid",
 *   "messages": [{ "id": "...", "role": "user", "content": "hello" }],
 *   "state":    {},
 *   "tools":    [],
 *   "context":  [],
 *   "forwardedProps": {}
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvokeAgentRequest {

    private String threadId;
    private String runId;
    private List<AguiMessage> messages;
    private Map<String, Object> state;
    private List<Object> tools;
    private List<Object> context;
    private Map<String, Object> forwardedProps;

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(final String threadId) {
        this.threadId = threadId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(final String runId) {
        this.runId = runId;
    }

    public List<AguiMessage> getMessages() {
        return messages;
    }

    public void setMessages(final List<AguiMessage> messages) {
        this.messages = messages;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void setState(final Map<String, Object> state) {
        this.state = state;
    }

    public List<Object> getTools() {
        return tools;
    }

    public void setTools(final List<Object> tools) {
        this.tools = tools;
    }

    public List<Object> getContext() {
        return context;
    }

    public void setContext(final List<Object> context) {
        this.context = context;
    }

    public Map<String, Object> getForwardedProps() {
        return forwardedProps;
    }

    public void setForwardedProps(final Map<String, Object> fp) {
        this.forwardedProps = fp;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AguiMessage {
        private String id;
        private String role;
        /**
         * Either a plain {@code String} or a {@code List<ContentPart>}.
         */
        private Object content;

        public String getId() {
            return id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        public String getRole() {
            return role;
        }

        public void setRole(final String role) {
            this.role = role;
        }

        public Object getContent() {
            return content;
        }

        public void setContent(final Object content) {
            this.content = content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DocumentSource {
        private String type; // "url" | "data"
        private String value;
        private String mimeType;

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(final String mimeType) {
            this.mimeType = mimeType;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentPart {
        private String type;
        private String text;
        private DocumentSource source;

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

        public DocumentSource getSource() {
            return source;
        }

        public void setSource(final DocumentSource source) {
            this.source = source;
        }
    }
}
