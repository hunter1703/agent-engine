package com.agentengine.engine.agents.processors;

import static com.agentengine.engine.api.utils.JsonUtils.parseJsonPayload;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.SubmitFinalAnswerTool;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.Violation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import io.reactivex.rxjava3.core.Single;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Normalizes model content and tool-call payloads for the engine.
 *
 * <p>Responsibilities:
 * - Parse text/thought tags into structured parts.
 * - Split mixed parts (text + tool payloads) into separate parts.
 * - Drop tool call/response parts from partial responses.
 * - Emit violations when partial responses include tool payloads.
 * - Convert tool call/response parts into text when required for model input.
 *
 * <p>Ownership: content normalization, parsing, and protocol hygiene.
 */
public final class Parser implements RequestProcessor, ResponseProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(Parser.class);
  private static final String FINAL_ANSWER_KEY = "finalAnswer";
  private static final Pattern TOOL_CALL_PATTERN =
      Pattern.compile(
          "\\{\\s*[\"']id[\"']\\s*:\\s*[\"']([^\"']*)[\"']\\s*,\\s*[\"']name[\"']\\s*:\\s*[\"']([^\"']*)[\"']\\s*,\\s*[\"']args[\"']\\s*:\\s*(\\{[^}]*})\\s*}",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern TOOL_CALL_TAG_PATTERN =
      Pattern.compile("</?tool_call\\s*/?>", Pattern.CASE_INSENSITIVE);
  private static final Pattern THOUGHT_TAG_PATTERN =
      Pattern.compile("<thought>(.*?)</thought>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private final ResponseFormatType responseFormat;
  private final boolean toolCallingEnabled;
  private final boolean parseToolCallsFromText;

  private Parser(
      final ResponseFormatType responseFormat,
      final boolean toolCallingEnabled,
      final boolean parseToolCallsFromText) {
    this.responseFormat = responseFormat == null ? ResponseFormatType.TEXT : responseFormat;
    this.toolCallingEnabled = toolCallingEnabled;
    this.parseToolCallsFromText = parseToolCallsFromText;
  }

  public static Parser create() {
    return new Parser(ResponseFormatType.TEXT, false, false);
  }

  @Override
  public Single<RequestProcessingResult> processRequest(
          final InvocationContext context, final LlmRequest request) {
    final List<Content> contents = new ArrayList<>();
    for (final Content content : CollectionUtils.nullSafeList(request.contents())) {
        final List<Part> toolCallParts = new ArrayList<>();
      final List<Part> toolResponseParts = new ArrayList<>();
      final List<Part> nonThoughtsPart = new ArrayList<>();

      for (final Part part : content.parts().orElse(List.of())) {
        if (part.functionResponse().isPresent()) {
          toolResponseParts.add(part);
        } else if (part.functionCall().isPresent()) {
          toolCallParts.add(part);
        } else if (!part.thought().orElse(false)) {
          nonThoughtsPart.add(part);
        }
      }

      final List<Part> parts = normalizeParts(nonThoughtsPart);
      if (shouldParseToolCallsFromText()) {
        parts.addAll(buildTextFormatPartsForToolCalls(toolCallParts));
      } else {
        parts.addAll(toolCallParts);
      }

      final List<Part> toolResponsePartsList = buildTextFormatPartsForToolResponses(toolResponseParts);

      if (CollectionUtils.isNotEmpty(parts) || CollectionUtils.isNotEmpty(toolResponsePartsList)) {
        if (CollectionUtils.isNotEmpty(parts)) {
            contents.add(content.toBuilder().parts(parts).build());
        }
        if (CollectionUtils.isNotEmpty(toolResponsePartsList)) {
          contents.add(Content.builder().role("user").parts(toolResponsePartsList).build());
        }
      }
    }

    return Single.just(
            RequestProcessingResult.create(request.toBuilder().contents(contents).build(), List.of()));
  }

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    if (response.content().isEmpty()) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final boolean isPartial = response.partial().orElse(false);

    final LlmResponse.Builder builder = response.toBuilder();
    Content updatedContent = response.content().get();

    if (isPartial) {
      final List<Part> normalizedParts = normalizeParts(updatedContent.parts().orElse(new ArrayList<>()));
      final boolean hasToolParts = normalizedParts.stream().anyMatch(part -> part.functionCall().isPresent() || part.functionResponse().isPresent());
      if (hasToolParts) {
        final String toolSummary = ToolUtils.summarizeToolParts(normalizedParts);
        final String correctionMessage = toolSummary.isBlank()
                ? "Tool calls and responses are not allowed in partial responses. Emit them only in non-partial turns."
                : "Tool calls and responses are not allowed in partial responses. Emit them only in non-partial turns."
                + " Following tool parts have been stripped : " + toolSummary + ".";
         RunStateUtils.getState(context).addViolation(Violation.builder("partial_tool_calls")
              .message("Tool calls in partial response")
              .correctionMessage(correctionMessage)
              .build());
      }
      updatedContent = updatedContent.toBuilder().parts(extractTextParts(normalizedParts)).build();
    } else {
      updatedContent = parse(updatedContent);
    }

    builder.content(updatedContent);
    return Single.just(ResponseProcessingResult.create(builder.build(), List.of(), Optional.empty()));
  }

  public Content parse(Content content) {
    if (content == null) {
      return null;
    }
    return normalizeContent(parseTextContent(content));
  }

  private Content parseJsonContent(final Content content) {
    final Content parsed = buildContentFromJsonText(content);
    if (parsed == null) {
      return content;
    }
    final List<Part> toolCallParts =
        CollectionUtils.nullSafeList(shouldParseToolCallsFromText() ? getToolCallParts(parsed) : getToolCallParts(content));
    final List<Part> filteredToolCallParts = filterToolCallParts(toolCallParts);
    final List<Part> otherParts =
        content.parts().orElse(Collections.emptyList()).stream()
            .filter(part -> part.functionCall().orElse(null) == null)
            .toList();
    final List<Part> allParts = new ArrayList<>(filteredToolCallParts);
    allParts.addAll(otherParts);
    return content.toBuilder().parts(allParts).build();
  }

  private static List<Part> getToolCallParts(final Content content) {
    final List<Part> parts =
        content.parts().isPresent()
            ? content.parts().orElse(Collections.emptyList())
            : Collections.emptyList();
    return parts.stream().filter(part -> part.functionCall().orElse(null) != null).toList();
  }

  private Content parseTextContent(Content content) {
    String processedText = content.text();
    List<Part> thoughtParts = new ArrayList<>(content.parts().orElse(List.of()).stream()
        .filter(part -> part.thought().orElse(false)).toList());

    List<Part> toolCallParts = new ArrayList<>();
    if (shouldParseToolCallsFromText()) {
      toolCallParts.addAll(parseToolCalls(processedText).stream().map(Parser::buildToolCallPart).toList());
      processedText = stripToolCallsBlock(processedText);
    } else {
      toolCallParts.addAll(getToolCallParts(content));
    }

    toolCallParts = filterToolCallParts(toolCallParts);

    // Extract thoughts from text if tags are present
    if (StringUtils.isNotBlank(processedText)) {
      Matcher thoughtMatcher = THOUGHT_TAG_PATTERN.matcher(processedText);
      while (thoughtMatcher.find()) {
        String thoughtText = thoughtMatcher.group(1).trim();
        if (StringUtils.isNotBlank(thoughtText)) {
          thoughtParts.add(Part.builder().text(thoughtText).thought(true).build());
        }
      }
      processedText = thoughtMatcher.replaceAll("").trim();
    }

    final String finalAnswer = StringUtils.isBlank(processedText) ? "" : processedText.trim();
    final List<Part> allParts = new ArrayList<>(toolCallParts);
    if (StringUtils.isNotBlank(finalAnswer)) {
      allParts.add(Part.builder().text(finalAnswer).build());
    }
    allParts.addAll(thoughtParts);

    return content.toBuilder().parts(allParts).build();
  }

  private static Part buildToolCallPart(final ToolCall toolCall) {
    return Part.builder()
        .functionCall(
            FunctionCall.builder().id(toolCall.id()).name(toolCall.name()).args(toolCall.args()))
        .build();
  }

  private Content buildContentFromJsonText(final Content content) {
    final String text = content.text();
    final Map<String, Object> payload = parseJsonPayload(text);
    if (CollectionUtils.isEmpty(payload)) {
      return null;
    }
    final String finalAnswer = CollectionUtils.getStringValueFromMap(payload, FINAL_ANSWER_KEY);
    final List<ToolCall> toolCalls =
        shouldParseToolCallsFromText() ? filterToolCalls(parseToolCallsFromJsonMap(payload)) : List.of();
    final Part finalAnswerPart = Part.builder().text(finalAnswer).build();
    final List<Part> toolCallParts = toolCalls.stream().map(Parser::buildToolCallPart).toList();
    final List<Part> allParts = new ArrayList<>();
    allParts.add(finalAnswerPart);
    allParts.addAll(toolCallParts);
    return content.toBuilder().parts(allParts).build();
  }

  private List<ToolCall> parseToolCallsFromJsonMap(final Map<String, Object> payload) {
    final Object toolCallsValue = payload.get("toolCalls");
    if (!(toolCallsValue instanceof List<?> list)) {
      return List.of();
    }
    final List<ToolCall> calls = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      final Object nameValue = map.get("name");
      if (nameValue == null) {
        continue;
      }
      final String toolName = nameValue.toString();
      final Object argsValue = map.get("args");
      @SuppressWarnings("unchecked")
      final Map<String, Object> args =
          argsValue instanceof Map<?, ?> argsMap ? (Map<String, Object>) argsMap : Map.of();
      final Object idValue = map.get("id");
      calls.add(new ToolCall(idValue == null ? null : idValue.toString(), toolName, args));
    }
    return calls;
  }

  private static List<ToolCall> parseToolCalls(final String content) {
    if (StringUtils.isBlank(content)) {
      return List.of();
    }

    final List<ToolCall> toolCalls = new ArrayList<>();
    final Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
    while (matcher.find()) {
      String id = matcher.group(1);
      String name = matcher.group(2);
      String argsStr = matcher.group(3);
      Map<String, Object> args;
      try {
        args = JsonUtils.fromJson(argsStr, new TypeReference<>() {});
      } catch (Exception e) {
        LOG.warn("Failed to parse tool call arguments: {}", argsStr, e);
        args = Map.of();
      }
      toolCalls.add(new ToolCall(id, name, args));
    }

    return toolCalls.isEmpty() ? List.of() : toolCalls;
  }

  private String stripToolCallsBlock(final String text) {
    if (StringUtils.isBlank(text) || !shouldParseToolCallsFromText()) {
      return text;
    }
    final String stripped = TOOL_CALL_PATTERN.matcher(text).replaceAll("").trim();
    return TOOL_CALL_TAG_PATTERN.matcher(stripped).replaceAll("").trim();
  }

  private static Content normalizeContent(final Content content) {
    if (content == null) {
      return null;
    }
    final List<Part> normalized = normalizeParts(content.parts().orElse(List.of()));
    return content.toBuilder().parts(normalized).build();
  }

  private static List<Part> normalizeParts(final List<Part> parts) {
    final List<Part> normalized = new ArrayList<>();
    for (final Part part : parts) {
      final boolean hasCall = part.functionCall().isPresent();
      final boolean hasResponse = part.functionResponse().isPresent();
      final boolean hasText = part.text().isPresent();
      final int distinctPayloads = (hasCall ? 1 : 0) + (hasResponse ? 1 : 0) + (hasText ? 1 : 0);

      if (distinctPayloads <= 1) {
        normalized.add(part);
        continue;
      }

      part.functionCall().ifPresent(call ->
          normalized.add(Part.builder().functionCall(call).build()));
      part.functionResponse().ifPresent(response ->
          normalized.add(Part.builder().functionResponse(response).build()));
      if (hasText) {
        normalized.add(
            Part.builder()
                .text(part.text().orElse(""))
                .thought(part.thought().orElse(false))
                .build());
      }
    }
    return normalized;
  }

  private static List<Part> extractTextParts(final List<Part> parts) {
    final List<Part> textParts = new ArrayList<>();
    for (final Part part : parts) {
      if (part.text().isPresent()) {
        textParts.add(part);
      }
    }
    return textParts;
  }

  private List<Part> buildJsonFormatParts(List<Part> nonThoughtsPart, List<Part> toolCallParts) {

    List<Part> result = new ArrayList<>();

    Map<String, Object> jsonMap = new LinkedHashMap<>();

    StringBuilder answerBuilder = new StringBuilder();
    for (Part part : nonThoughtsPart) {
      if (part.text().isPresent()) {
        answerBuilder.append(part.text().get()).append(" ");
      }
    }
    final String answer = answerBuilder.toString().trim();
    if (StringUtils.isNotBlank(answer)) {
      jsonMap.put(FINAL_ANSWER_KEY, answer);
    }

    if (shouldParseToolCallsFromText()) {
      List<Map<String, Object>> toolCallsList = new ArrayList<>();
      for (Part part : toolCallParts) {
        FunctionCall call = part.functionCall().orElse(null);
        if (call == null) {
          continue;
        }
        Map<String, Object> toolCallMap = new LinkedHashMap<>();
        call.id().ifPresent(id -> toolCallMap.put("id", id));
        call.name().ifPresent(name -> toolCallMap.put("name", name));
        toolCallMap.put("args", call.args());
        toolCallsList.add(toolCallMap);
      }
      if (!toolCallsList.isEmpty()) {
        jsonMap.put("toolCalls", toolCallsList);
      }
    }

    if (!jsonMap.isEmpty()) {
      String jsonText = JsonUtils.toJson(jsonMap);
      result.add(Part.builder().text(jsonText).build());
    }

    if (!shouldParseToolCallsFromText()) {
      result.addAll(toolCallParts);
    }
    return result;
  }

  private List<Part> buildTextFormatPartsForToolCalls(List<Part> toolCallParts) {
    if (shouldParseToolCallsFromText()) {
      StringBuilder textBuilder = new StringBuilder();
      for (Part part : toolCallParts) {
        FunctionCall call = part.functionCall().orElse(null);
        if (call == null) {
          continue;
        }
        String argsJson = JsonUtils.toJson(call.args());
        String toolCallText =
            String.format(
                "\n{'id': '%s', 'name': '%s', 'args': %s}",
                call.id().orElse(""), call.name().orElse(""), argsJson);
        textBuilder.append(toolCallText);
      }
      if (!textBuilder.isEmpty()) {
        return List.of(Part.fromText(textBuilder.toString()));
      }
    }
    return List.of();
  }

  private List<Part> buildTextFormatPartsForToolResponses(final List<Part> toolResponseParts) {
    if (CollectionUtils.isEmpty(toolResponseParts)) {
      return List.of();
    }
    if (!shouldParseToolCallsFromText()) {
        return toolResponseParts;
    }
    final List<Part> parsedResponseParts = new ArrayList<>();
    for (final Part part : toolResponseParts) {
      final FunctionResponse response = part.functionResponse().orElse(null);
      if (response == null) {
        continue;
      }
      final String responseText =
              String.format(
                      "Tool Response [%s]: %s", response.name().orElse("unknown"), response.response());
      parsedResponseParts.add(Part.fromText(responseText));
    }
    return parsedResponseParts;
  }

  private boolean shouldParseToolCallsFromText() {
    // for non tool calling models, the final answer tool call still needs to happen from text
    return parseToolCallsFromText || !toolCallingEnabled;
  }

  private List<ToolCall> filterToolCalls(final List<ToolCall> toolCalls) {
    if (toolCallingEnabled) {
      return toolCalls;
    }
    return CollectionUtils.nullSafeList(toolCalls).stream()
        .filter(call -> SubmitFinalAnswerTool.TOOL_NAME.equals(call.name()))
        .toList();
  }

  private List<Part> filterToolCallParts(final List<Part> parts) {
    if (!toolCallingEnabled) {
      return CollectionUtils.nullSafeList(parts).stream()
          .filter(ToolUtils::callsFinalAnswerTool)
          .toList();
    }
    return CollectionUtils.nullSafeList(parts);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ResponseFormatType responseFormat = ResponseFormatType.TEXT;
    private boolean toolCallingEnabled;
    private boolean parseToolCallsFromText = false;

    public Builder withResponseFormat(final ResponseFormatType responseFormat) {
      this.responseFormat = responseFormat == null ? ResponseFormatType.TEXT : responseFormat;
      return this;
    }

    public Builder toolCallingEnabled(final boolean toolCallingEnabled) {
      this.toolCallingEnabled = toolCallingEnabled;
      return this;
    }

    public Builder parseToolCallsFromText(final boolean parseToolCallsFromText) {
      this.parseToolCallsFromText = parseToolCallsFromText;
      return this;
    }

    public Parser build() {
      return new Parser(responseFormat, toolCallingEnabled, parseToolCallsFromText);
    }
  }

  private record ToolCall(String id, String name, Map<String, Object> args) {
    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final ToolCall toolCall = (ToolCall) o;
      return Objects.equals(id, toolCall.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }
}
