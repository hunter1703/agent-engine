package com.agentengine.util.agents;

import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.List;
import java.util.Optional;

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

  public static Content userMessage(final List<Part> parts) {
    return Content.builder().role(Constants.AUTHOR_USER).parts(parts).build();
  }

  /** Whether {@code content} is a user-role message rather than a tool result. */
  public static boolean isUserSentMessage(final Content content) {
    return content != null && isUserContent(content) && getToolResponseParts(content).isEmpty();
  }

  private static boolean isUserContent(final Content content) {
    final Optional<String> role = content.role();
    return role.isEmpty() || Constants.AUTHOR_USER.equalsIgnoreCase(role.get());
  }

  public static boolean isModelContent(final Content content) {
    if (content == null) {
      return false;
    }
    final Optional<String> role = content.role();
    return role.isPresent()
        && (Constants.AUTHOR_MODEL.equalsIgnoreCase(role.get())
            || Constants.AUTHOR_ASSISTANT.equalsIgnoreCase(role.get()));
  }
}
