package com.agentengine.util.agents;

import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.flows.llmflows.Functions;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.*;
import java.util.stream.Collectors;

/** Utilities for generic content/message extraction and indexing. */
public final class ContentUtils {

  private static final String CURRENT_TIME_OPEN = "<current_time>";
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

  public static Content userMessage(final List<Part> parts) {
    return Content.builder().role(Constants.AUTHOR_USER).parts(parts).build();
  }

  /**
   * A run's start message as the model reads it: its parts between {@code <user_message>} tags,
   * led by {@code currentTime}, when it was sent.
   */
  public static Content withCurrentTime(final Content content, final String currentTime) {
    final List<Part> parts = new ArrayList<>();
    parts.add(Part.fromText(CURRENT_TIME_OPEN + "\n" + currentTime + "\n</current_time>\n\n"));
    parts.add(Part.fromText(USER_MESSAGE_OPEN));
    parts.addAll(content.parts().orElse(List.of()));
    parts.add(Part.fromText(USER_MESSAGE_CLOSE));
    return content.toBuilder().parts(parts).build();
  }

  /** Whether {@code content} is a message {@link #withCurrentTime} made. */
  public static boolean startsWithCurrentTime(final Content content) {
    return content.parts().flatMap(parts -> parts.stream().findFirst())
        .flatMap(Part::text)
        .filter(text -> text.startsWith(CURRENT_TIME_OPEN))
        .isPresent();
  }

  /** The text of {@code content}, without what {@link #withCurrentTime} adds around a message. */
  public static String textWithoutCurrentTime(final Content content) {
    final boolean framed = startsWithCurrentTime(content);
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

  /**
   * Rewrites the tool calls in a conversation as plain-text user messages describing completed
   * actions.
   *
   * <p>Each call is taken out of the model's turn, which keeps only its text, and becomes one user
   * message holding the action's name, its inputs and its result, in place of the result. A call
   * with no result becomes a message without one, right after the turn that made it. The messages
   * hold no call syntax, so a model that sees them has no call of its own to continue.
   */
  public static List<Content> withToolCallsAsMessages(final List<Content> contents) {
    final Map<String, FunctionCall> idVsCall = new HashMap<>();
    final Set<String> answeredIds = new HashSet<>();
    for (final Content content : contents) {
      for (final Part part : content.parts().orElse(List.of())) {
        part.functionCall()
            .ifPresent(call -> call.id().ifPresent(id -> idVsCall.put(id, call)));
        part.functionResponse().flatMap(FunctionResponse::id).ifPresent(answeredIds::add);
      }
    }

    final List<Content> rewritten = new ArrayList<>();
    for (final Content content : contents) {
      final List<Part> textParts = new ArrayList<>();
      final List<String> toolMessages = new ArrayList<>();
      final List<String> unansweredToolMessages = new ArrayList<>();
      for (final Part part : content.parts().orElse(List.of())) {
        if (part.functionCall().isPresent()) {
          final FunctionCall call = part.functionCall().get();
          if (call.id().map(answeredIds::contains).orElse(false)) {
            continue;
          }
          unansweredToolMessages.add(toolMessage(call.name().orElse(""), call.args().orElse(Map.of()), null));
        } else if (part.functionResponse().isPresent()) {
          final FunctionResponse response = part.functionResponse().get();
          final FunctionCall call =
              response
                  .id()
                  .map(idVsCall::get)
                  .orElseThrow(
                      () -> new IllegalStateException("Tool result has no call: " + response));
          toolMessages.add(
              toolMessage(
                  response.name().orElse(""),
                  call.args().orElse(Map.of()),
                  response.response().orElse(Map.of())));
        } else {
          textParts.add(part);
        }
      }

      if (!toolMessages.isEmpty()) {
        textParts.addAll(toolMessages.stream().map(Part::fromText).toList());
        rewritten.add(userMessage(textParts));
        continue;
      }
      if (!textParts.isEmpty()) {
        rewritten.add(content.toBuilder().parts(textParts).build());
      }
      if (!unansweredToolMessages.isEmpty()) {
        rewritten.add(userMessage(unansweredToolMessages.stream().map(Part::fromText).toList()));
      }
    }
    return rewritten;
  }

  private static boolean isUserRole(final Content content) {
    final Optional<String> role = content.role();
    return role.isEmpty() || Constants.AUTHOR_USER.equalsIgnoreCase(role.get());
  }

  private static String toolMessage(
      final String name, final Map<String, Object> inputs, final Map<String, Object> result) {
    final StringBuilder toolMessage =
        new StringBuilder("Completed action\nAction: ")
            .append(name)
            .append("\nInputs:")
            .append(keyValueLines(inputs));
    if (result != null) {
      toolMessage.append("\nResult:").append(keyValueLines(result));
    }
    return toolMessage.toString();
  }

  // One "- key: value" line per entry; a string value is shown as is, any other value as JSON.
  private static String keyValueLines(final Map<String, Object> values) {
    if (values.isEmpty()) {
      return " none";
    }
    final StringBuilder lines = new StringBuilder();
    values.forEach(
        (key, value) ->
            lines
                .append("\n- ")
                .append(key)
                .append(": ")
                .append(value instanceof String text ? text : JsonUtils.toJson(value)));
    return lines.toString();
  }
}
