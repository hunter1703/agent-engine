package com.agentengine.util.agents.beans.tools;

import java.util.Map;

public abstract class ToolOutput<T> {

    public abstract T toResult();

    public static <S> ToolOutput<S> direct(final S content) {
        return new Direct<>(content);
    }

    public static ToolOutput<Map<String, Object>> knowledge(final String knowledgeId) {
        return knowledge(knowledgeId, "Please refer to the knowledge in order to search.");
    }

    public static ToolOutput<Map<String, Object>> knowledge(final String knowledgeId, final String hint) {
        return new Knowledge(knowledgeId, hint);
    }

    public static final class Direct<S> extends ToolOutput<S> {
        private final S content;

        private Direct(final S content) {
            this.content = content;
        }

        @Override
        public S toResult() {
            return content;
        }
    }

    public static final class Knowledge extends ToolOutput<Map<String, Object>> {
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
        public Map<String, Object> toResult() {
            return Map.of("knowledgeId", knowledgeId, "hint", hint);
        }
    }
}
