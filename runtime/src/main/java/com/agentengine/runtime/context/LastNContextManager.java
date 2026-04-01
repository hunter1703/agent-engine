package com.agentengine.runtime.context;

import com.agentengine.runtime.utils.ContentUtils;
import com.google.genai.types.Content;
import java.util.ArrayList;
import java.util.List;

public final class LastNContextManager extends BaseContextManager {
    public LastNContextManager(final int keepLastTokens) {
        super(contents -> trim(contents, Math.max(1024, keepLastTokens)));
    }

    private static List<Content> trim(final List<Content> contents, final int tokenBudget) {
        if (contents == null || contents.isEmpty()) {
            return List.of();
        }

        int consumed = 0;
        final List<Content> recent = new ArrayList<>();

        for (int i = contents.size() - 1; i >= 0; i--) {
            final Content content = contents.get(i);
            final int contentTokens = ContentUtils.estimateTokens(content);
            if (!recent.isEmpty() && consumed + contentTokens > tokenBudget) {
                break;
            }
            recent.add(content);
            consumed += contentTokens;
        }

        final List<Content> result = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            result.add(recent.get(i));
        }
        return result;
    }
}
