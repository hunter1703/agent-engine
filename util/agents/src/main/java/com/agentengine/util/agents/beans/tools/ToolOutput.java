package com.agentengine.util.agents.beans.tools;

import java.util.Map;

public abstract class ToolOutput {

    public abstract Map<String, Object> toMap();

    public static ToolOutput direct(final Map<String, Object> content) {
        return new Direct(content);
    }

    public static ToolOutput knowledge(final String knowledgeId) {
        return knowledge(knowledgeId, "Please refer to the knowledge in order to search.");
    }

    public static ToolOutput knowledge(final String knowledgeId, final String hint) {
        return new Knowledge(knowledgeId, hint);
    }

    public static final class Direct extends ToolOutput {
        private final Map<String, Object> content;

        private Direct(final Map<String, Object> content) {
            this.content = content;
        }

        @Override
        public Map<String, Object> toMap() {
            return content;
        }
    }

    public static final class Knowledge extends ToolOutput {
        private final String knowledgeId;
        private final String hint;

        private Knowledge(final String knowledgeId, final String hint) {
            this.knowledgeId = knowledgeId;
            this.hint = hint;
        }

        public String getKnowledgeId() {
            return knowledgeId;
        }

        public String getHint() {
            return hint;
        }

        @Override
        public Map<String, Object> toMap() {
            return Map.of("knowledgeId", knowledgeId, "hint", hint);
        }
    }
}
