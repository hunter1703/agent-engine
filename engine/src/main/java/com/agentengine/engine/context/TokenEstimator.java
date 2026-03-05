package com.agentengine.engine.context;

import com.agentengine.engine.api.utils.StringUtils;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;

/** Lightweight token estimator used for context compaction thresholds. */
public final class TokenEstimator {
  public static final TokenEstimator INSTANCE = new TokenEstimator();
  private TokenEstimator() {}

  public int estimateTokens(final List<Content> contents) {
    if (contents == null || contents.isEmpty()) {
      return 0;
    }
    int total = 0;
    for (final Content content : contents) {
      total += estimateTokens(content);
    }
    return total;
  }

  public int estimateTokens(final Content content) {
    if (content == null) {
      return 0;
    }
    final String text = content.text();
    if (StringUtils.isNotBlank(text)) {
      return estimateTokens(text);
    }
    int total = 0;
    for (final Part part : content.parts().orElse(List.of())) {
      total += estimateTokens(part.text().orElse(""));
      total += part.functionCall().isPresent() ? 16 : 0;
      total += part.functionResponse().isPresent() ? 16 : 0;
    }
    return total;
  }

  public int estimateTokens(final String text) {
    if (StringUtils.isBlank(text)) {
      return 0;
    }
    return text.trim().split("\\s+").length;
  }
}
