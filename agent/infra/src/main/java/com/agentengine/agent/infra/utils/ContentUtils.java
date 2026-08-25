package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.*;

/** Utilities for generic content/message extraction and indexing. */
public final class ContentUtils {

  private ContentUtils() {}

  public static String extractLatestUserText(final List<Content> contents) {
    final List<Content> safeContents = CollectionUtils.nullSafeList(contents);
    for (int i = safeContents.size() - 1; i >= 0; i--) {
      final Content content = safeContents.get(i);
      if (content == null) {
        continue;
      }
      final Optional<String> role = content.role();
      if (role.isPresent() && !Constants.AUTHOR_USER.equalsIgnoreCase(role.get())) {
        continue;
      }
      final String text = content.text();
      if (StringUtils.isNotBlank(text)) {
        return text.trim();
      }
    }
    return "";
  }

  public static String getLatestModelText(final List<Content> contents) {
    final List<Content> safeContents = CollectionUtils.nullSafeList(contents);
    for (int i = safeContents.size() - 1; i >= 0; i--) {
      final Content content = safeContents.get(i);
      if (content == null) {
        continue;
      }
      final Optional<String> role = content.role();
      if (role.isPresent() && Constants.AUTHOR_USER.equalsIgnoreCase(role.get())) {
        continue;
      }
      if (!hasVisibleText(content)) {
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
      if (role.isEmpty() || Constants.AUTHOR_USER.equalsIgnoreCase(role.get())) {
        return i;
      }
    }
    return -1;
  }

  public static int estimateTokens(final Content content) {
    if (content == null) {
      return 0;
    }
    return StringUtils.estimateTokens(content.text());
  }

  public static boolean isEmptyPart(final Content content) {
    if (content == null) {
      return true;
    }
    return content.parts().orElse(List.of()).stream().allMatch(ContentUtils::isEmptyPart);
  }

  public static boolean isEmptyPart(final Part part) {
    return part == null
        || (part.functionCall().isEmpty()
            && part.functionResponse().isEmpty()
            && part.codeExecutionResult().isEmpty()
            && part.executableCode().isEmpty()
            && part.fileData().isEmpty()
            && part.inlineData().isEmpty()
            && part.mediaResolution().isEmpty()
            && part.videoMetadata().isEmpty()
            && part.thoughtSignature().isEmpty()
            && StringUtils.isBlank(part.text().orElse(null)));
  }

  public static boolean isFunctionCall(final Part part, final String toolName) {
    return part.functionCall().map(call -> toolName.equals(call.name().orElse(null))).orElse(false);
  }

  public static boolean isFunctionResponse(final Part part, final String toolName) {
    return part.functionResponse()
        .map(response -> toolName.equals(response.name().orElse(null)))
        .orElse(false);
  }

  public static List<Part> getToolCallParts(final Content content) {
    return content == null
        ? List.of()
        : content.parts().orElse(List.of()).stream()
            .filter(part -> part.functionCall().isPresent())
            .toList();
  }

  public static List<Part> getToolResponseParts(final Content content) {
    return content == null
        ? List.of()
        : content.parts().orElse(List.of()).stream()
            .filter(part -> part.functionResponse().isPresent())
            .toList();
  }

  public static boolean hasVisibleText(final Content content) {
    if (content == null) {
      return false;
    }
    return content.parts().orElse(List.of()).stream()
        .filter(part -> !part.thought().orElse(false))
        .map(Part::text)
        .flatMap(Optional::stream)
        .anyMatch(StringUtils::isNotBlank);
  }

  public static List<Content> stripThoughtParts(final List<Content> contents) {
    return CollectionUtils.nullSafeList(contents).stream()
        .map(
            content ->
                content.toBuilder()
                    .parts(
                        content.parts().orElse(List.of()).stream()
                            .filter(part -> !part.thought().orElse(false))
                            .toList())
                    .build())
        .filter(content -> !content.parts().orElse(List.of()).isEmpty())
        .toList();
  }

  public static Content stripNonToolParts(final Content content) {
    return content.toBuilder()
        .parts(
            content.parts().orElse(List.of()).stream()
                .filter(part -> part.functionCall().isEmpty() && part.functionResponse().isEmpty())
                .toList())
        .build();
  }

  /** {@link MessagePart} has only one variant, {@link MessagePart.TextPart}. */
  public static List<MessagePart.TextPart> textParts(final List<MessagePart> parts) {
    return CollectionUtils.nullSafeList(parts).stream()
        .map(MessagePart.TextPart.class::cast)
        .toList();
  }

  public static Content buildUserContent(final List<MessagePart.TextPart> textParts) {
    final StringBuilder text = new StringBuilder();
    for (final MessagePart.TextPart textPart : textParts) {
      if (!text.isEmpty()) {
        text.append("\n");
      }
      text.append(textPart.text());
    }
    return Content.builder()
        .role(Constants.AUTHOR_USER)
        .parts(List.of(Part.fromText(text.toString())))
        .build();
  }

  public static Content buildResumeContent(final Collection<ResumeRequest> resumeRequests) {
    final List<Part> parts =
        CollectionUtils.nullSafeList(resumeRequests).stream()
            .map(
                resumeRequest ->
                    EventUtils.buildResumeAnswerEvent(
                        resumeRequest.getInterruptId(),
                        resumeRequest.getAccepted(),
                        resumeRequest.getAnswer()))
            .flatMap(event -> event.content().stream())
            .flatMap(content -> content.parts().stream())
            .flatMap(List::stream)
            .toList();
    return Content.builder().role(Constants.AUTHOR_USER).parts(parts).build();
  }
}
