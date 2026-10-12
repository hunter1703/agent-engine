package com.agentengine.util.agents;

import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities for generic content/message extraction and indexing. */
public final class ContentUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ContentUtils.class);
  private static final String CURRENT_USER_CONTEXT_OPEN = "<current_user_context>";
  private static final String CURRENT_USER_CONTEXT_CLOSE = "</current_user_context>";
  private static final String USER_MESSAGE_OPEN = "<user_message>\n";
  private static final String USER_MESSAGE_CLOSE = "\n</user_message>";

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

  public static int estimateTokens(final Content content) {
    if (content == null) {
      return 0;
    }
    return StringUtils.estimateTokens(content.text());
  }

  public static List<Part> getToolResponseParts(final Content content) {
    return content == null
        ? List.of()
        : content.parts().orElse(List.of()).stream()
            .filter(part -> part.functionResponse().isPresent())
            .toList();
  }

  public static List<FunctionCall> getFunctionCalls(final Content content, final String toolName) {
    if (content == null || StringUtils.isBlank(toolName)) {
      return List.of();
    }
    return content.parts().orElse(List.of()).stream()
        .map(Part::functionCall)
        .flatMap(Optional::stream)
        .filter(call -> toolName.equals(call.name().orElse("")))
        .toList();
  }

  public static List<FunctionResponse> getFunctionResponses(
      final Content content, final String toolName) {
    if (content == null || StringUtils.isBlank(toolName)) {
      return List.of();
    }
    return content.parts().orElse(List.of()).stream()
        .map(Part::functionResponse)
        .flatMap(Optional::stream)
        .filter(response -> toolName.equals(response.name().orElse("")))
        .toList();
  }

  public static boolean isFunctionCall(final Part part, final String toolName) {
    return part.functionCall()
        .flatMap(FunctionCall::name)
        .filter(name -> name.equals(toolName))
        .isPresent();
  }

  public static boolean isFunctionResponse(final Part part, final String toolName) {
    return part.functionResponse()
        .flatMap(FunctionResponse::name)
        .filter(name -> name.equals(toolName))
        .isPresent();
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

  /** Whether {@code content} is a resume: only answers to confirmation requests. */
  public static boolean isResumeContent(final Content content) {
    final List<Part> parts = content.parts().orElse(List.of());
    return !parts.isEmpty()
        && parts.stream()
            .allMatch(
                part ->
                    part.functionResponse()
                        .flatMap(FunctionResponse::name)
                        .filter(Functions.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME::equals)
                        .isPresent());
  }

  public static Content addAttachmentsToContent(
      final Content content,
      final List<AgentFileDetails> attachments,
      final FileService fileService) {
    final StringBuilder text = new StringBuilder();
    final List<Part> binaryParts = new ArrayList<>();
    for (final AgentFileDetails attachment : CollectionUtils.nullSafeList(attachments)) {
      if (StringUtils.isNotBlank(attachment.knowledgeId())) {
        text.append("\n(The attached file \"")
            .append(attachment.name())
            .append("\" is indexed as knowledgeId \"")
            .append(attachment.knowledgeId())
            .append("\").");
      } else {
        try (InputStream stream = fileService.getContent(attachment.toFileDetails())) {
          byte[] bytes = stream.readAllBytes();
          if (bytes.length > 0) {
            binaryParts.add(Part.fromBytes(bytes, attachment.mimeType()));
          }
        } catch (IOException ioException) {
          LOG.warn("Failed to read attachment file {}.", attachment.source(), ioException);
        }
      }
    }
    final List<Part> parts = new ArrayList<>(content.parts().orElse(List.of()));
    if (!text.isEmpty()) {
      parts.add(Part.fromText(text.toString()));
    }
    if (CollectionUtils.isNotEmpty(binaryParts)) {
      parts.addAll(binaryParts);
    }
    return content.toBuilder().parts(parts).build();
  }

  public static Content userMessage(final List<Part> parts) {
    return Content.builder().role(Constants.AUTHOR_USER).parts(parts).build();
  }

  /**
   * A run's start message as the model reads it: its parts between {@code <user_message>} tags, led
   * by {@code currentTime}, when it was sent.
   */
  public static Content withUserContext(final Content content, final String currentTime) {
    final List<Part> parts = new ArrayList<>();
    parts.add(
        Part.fromText(
            CURRENT_USER_CONTEXT_OPEN
                + "\n"
                + currentTime
                + "\n"
                + CURRENT_USER_CONTEXT_CLOSE
                + "\n\n"));
    parts.add(Part.fromText(USER_MESSAGE_OPEN));
    parts.addAll(content.parts().orElse(List.of()));
    parts.add(Part.fromText(USER_MESSAGE_CLOSE));
    return content.toBuilder().parts(parts).build();
  }

  /** Whether {@code content} is a message {@link #withUserContext} made. */
  public static boolean startsWithUserContext(final Content content) {
    return content
        .parts()
        .flatMap(parts -> parts.stream().findFirst())
        .flatMap(Part::text)
        .filter(text -> text.startsWith(CURRENT_USER_CONTEXT_OPEN))
        .isPresent();
  }

  /** The text of {@code content}, without what {@link #withUserContext} adds around a message. */
  public static String textWithoutCurrentTime(final Content content) {
    final boolean framed = startsWithUserContext(content);
    final List<Part> parts = content.parts().orElse(List.of());
    // The time, the opening tag, then the message, then the closing tag.
    return parts.subList(framed ? 2 : 0, framed ? parts.size() - 1 : parts.size()).stream()
        .flatMap(part -> part.text().stream())
        .collect(Collectors.joining());
  }

  /** Whether {@code content} is a user-role message rather than a tool result. */
  public static boolean isUserMessage(final Content content) {
    return content != null && isUserRole(content) && getToolResponseParts(content).isEmpty();
  }

  private static boolean isUserRole(final Content content) {
    final Optional<String> role = content.role();
    return role.isEmpty() || Constants.AUTHOR_USER.equalsIgnoreCase(role.get());
  }
}
