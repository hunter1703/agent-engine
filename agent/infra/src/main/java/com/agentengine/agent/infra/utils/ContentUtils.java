package com.agentengine.agent.infra.utils;

import com.agentengine.agent.api.model.MessagePart;
import com.agentengine.util.agents.AgentFileDetails;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
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

  /**
   * Index of the latest user-role content that is not a tool result. Tool results are user-role
   * too, so this is the message a run started from.
   */
  public static int findLatestUserMessageIndex(final List<Content> contents) {
    final List<Content> safeContents = CollectionUtils.nullSafeList(contents);
    for (int i = safeContents.size() - 1; i >= 0; i--) {
      final Content content = safeContents.get(i);
      if (content != null && isUserRole(content) && getToolResponseParts(content).isEmpty()) {
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

  /** Drops any non-text part (e.g. {@link MessagePart.BinaryPart}) rather than failing on it. */
  public static List<MessagePart.TextPart> textParts(final List<MessagePart> parts) {
    return CollectionUtils.nullSafeList(parts).stream()
        .filter(MessagePart.TextPart.class::isInstance)
        .map(MessagePart.TextPart.class::cast)
        .toList();
  }

  public static Content buildUserContent(
      final List<MessagePart.TextPart> textParts, final List<AgentFileDetails> knowledges) {
    final StringBuilder text = new StringBuilder();
    for (final MessagePart.TextPart textPart : textParts) {
      if (!text.isEmpty()) {
        text.append("\n");
      }
      text.append(textPart.text());
    }

    final Content content =
        Content.builder()
            .role(Constants.AUTHOR_USER)
            .parts(List.of(Part.fromText(text.toString())))
            .build();
    return addAttachmentsToContent(content, knowledges);
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

  /** Whether {@code content} is a resume: only answers to confirmation requests, as built above. */
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
      final Content content, final List<AgentFileDetails> attachments) {
    final StringBuilder text = new StringBuilder();
    for (final AgentFileDetails attachment : CollectionUtils.nullSafeList(attachments)) {
      if (StringUtils.isBlank(attachment.knowledgeId())) {
        continue;
      }
      text.append("\n(The attached file \"")
          .append(attachment.name())
          .append("\" is indexed as knowledgeId \"")
          .append(attachment.knowledgeId())
          .append("\").");
    }
    if (text.isEmpty()) {
      return content;
    }
    final List<Part> parts = new ArrayList<>(content.parts().orElse(List.of()));
    parts.add(Part.fromText(text.toString()));
    return content.toBuilder().parts(parts).build();
  }

  private static boolean isUserRole(final Content content) {
    final Optional<String> role = content.role();
    return role.isEmpty() || Constants.AUTHOR_USER.equalsIgnoreCase(role.get());
  }
}
