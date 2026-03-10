package com.agentengine.engine.context;

import com.agentengine.util.StringUtils;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;

public final class LastNContextManager extends BaseContextManager {
  public LastNContextManager(final int keepLastTokens) {
    super(contents -> trim(contents, keepLastTokens));
  }

  private static List<Content> trim(final List<Content> contents, final int keepLastTokens) {
    final int tokenBudget = Math.max(1, keepLastTokens);
    if (contents == null || contents.isEmpty()) {
      return List.of();
    }

    int consumed = 0;
    boolean trimmed = false;
    final List<Content> recent = new ArrayList<>();

    for (int i = contents.size() - 1; i >= 0; i--) {
      final Content content = contents.get(i);
      final int contentTokens = estimateTokens(content);
      if (!recent.isEmpty() && consumed + contentTokens > tokenBudget) {
        trimmed = true;
        break;
      }
      recent.add(content);
      consumed += contentTokens;
      if (consumed >= tokenBudget) {
        trimmed = i > 0;
        break;
      }
    }

    final List<Content> result = new ArrayList<>();
    if (trimmed) {
      result.add(
          Content.builder()
              .role("user")
              .parts(List.of(Part.fromText("Following is the trimmed conversation")))
              .build());
    }
    for (int i = recent.size() - 1; i >= 0; i--) {
      result.add(recent.get(i));
    }
    return result;
  }

  private static int estimateTokens(final Content content) {
    if (content == null) {
      return 0;
    }
    final String text = content.text();
    if (StringUtils.isBlank(text)) {
      return 1;
    }
    return Math.max(1, text.trim().split("\\s+").length);
  }
}
