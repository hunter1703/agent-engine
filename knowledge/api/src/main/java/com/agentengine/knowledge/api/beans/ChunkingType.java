package com.agentengine.knowledge.api.beans;

import java.util.Locale;

public enum ChunkingType {
    UNKNOWN,
    /** Recursively splits on paragraph, sentence, then character boundaries (default). */
    RECURSIVE,
    /** Splits on sentence boundaries — good for Q&A corpora. */
    SENTENCE,
    /** Splits on paragraph boundaries — good for narrative text. */
    PARAGRAPH;

    public static ChunkingType valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return ChunkingType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
