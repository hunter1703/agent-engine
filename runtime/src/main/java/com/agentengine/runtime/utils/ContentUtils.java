package com.agentengine.runtime.utils;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Optional;

/** Utilities for generic content/message extraction and indexing. */
public final class ContentUtils {

  private ContentUtils() {
  }

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

  public static int estimateTokens(final Content content) {
    if (content == null) {
      return 0;
    }
    return StringUtils.estimateTextContent(content.text());
  }

  public static boolean isEmptyPart(final Content content) {
    if (content == null) {
      return true;
    }
    return content.parts().orElse(List.of()).stream().allMatch(ContentUtils::isEmptyPart);
  }

  public static boolean isEmptyPart(final Part part) {
    return part == null || (part.functionCall().isEmpty() && part.functionResponse().isEmpty() && part.codeExecutionResult().isEmpty()
        && part.executableCode().isEmpty() && part.fileData().isEmpty() && part.inlineData().isEmpty() && part.mediaResolution().isEmpty()
        && part.videoMetadata().isEmpty() && part.thoughtSignature().isEmpty() && StringUtils.isBlank(part.text().orElse(null)));
  }

  public static List<Part> getToolCallParts(final Content content) {
    return content.parts().orElse(List.of()).stream().filter(part -> part.functionCall().isPresent()).toList();
  }

  public static List<Part> getToolResponseParts(final Content content) {
    return content.parts().orElse(List.of()).stream().filter(part -> part.functionResponse().isPresent()).toList();
  }

  public static boolean hasVisibleText(final Content content) {
    if (content == null) {
      return false;
    }
    return content.parts().orElse(List.of()).stream().filter(part -> !part.thought().orElse(false)).map(Part::text)
        .flatMap(Optional::stream).anyMatch(StringUtils::isNotBlank);
  }

  public static List<Content> stripThoughtParts(final List<Content> contents) {
    return CollectionUtils.nullSafeList(contents).stream()
        .map(c -> c.toBuilder().parts(c.parts().orElse(List.of()).stream().filter(p -> !p.thought().orElse(false)).toList()).build())
        .filter(c -> !c.parts().orElse(List.of()).isEmpty())
        .toList();
  }

  public static Content stripToolParts(final Content content) {
    return content.toBuilder().parts(content.parts().orElse(List.of()).stream().filter(p -> p.functionCall().isEmpty() && p.functionResponse().isEmpty()).toList()).build();
  }
}
