package com.agentengine.engine.api.utils;

import com.agentengine.util.StringUtils;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import java.util.List;
import java.util.Optional;

/** Utilities for generic content/message extraction and indexing. */
public final class ContentUtils {

  private ContentUtils() {}

  public static String extractLatestUserText(final LlmRequest request) {
    if (request == null) {
      return "";
    }
    return extractLatestUserText(request.contents());
  }

  public static String extractLatestUserText(final List<Content> contents) {
    final List<Content> safeContents = CollectionUtils.nullSafeList(contents);
    for (int i = safeContents.size() - 1; i >= 0; i--) {
      final Content content = safeContents.get(i);
      if (content == null) {
        continue;
      }
      final Optional<String> role = content.role();
      if (role.isPresent() && !"user".equalsIgnoreCase(role.get())) {
        continue;
      }
      final String text = content.text();
      if (StringUtils.isNotBlank(text)) {
        return text.trim();
      }
    }
    return "";
  }

  public static int findLatestUserContentIndex(final List<Content> contents) {
    final List<Content> safeContents = CollectionUtils.nullSafeList(contents);
    for (int i = safeContents.size() - 1; i >= 0; i--) {
      final Content content = safeContents.get(i);
      if (content == null) {
        continue;
      }
      final Optional<String> role = content.role();
      if (role.isEmpty() || "user".equalsIgnoreCase(role.get())) {
        return i;
      }
    }
    return -1;
  }
}
