package com.agentengine.engine.utils;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.alibaba.fastjson2.TypeReference;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.agentengine.engine.utils.AgentUtils.parseJsonPayload;

public class Parser implements RequestProcessor, ResponseProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(Parser.class);
  private static final String FINAL_ANSWER_KEY = "finalAnswer";
  private static final String THOUGHTS_KEY = "thoughts";
  private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
      "\\{\\s*[\"']id[\"']\\s*:\\s*[\"']([^\"']*)[\"']\\s*,\\s*[\"']name[\"']\\s*:\\s*[\"']([^\"']*)[\"']\\s*,\\s*[\"']args[\"']\\s*:\\s*(\\{[^}]*})\\s*\\}",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private ResponseFormatType responseFormat = ResponseFormatType.TEXT;
  private boolean toolCallingEnabled = false;
  private boolean parseToolCallsFromText = true;
  private boolean parseThoughtsFromText = true;
  private boolean thoughtsEnabled = false;
  private String thoughtsStartTag = null;
  private String thoughtsEndTag = null;

  public static Parser create() {
    return new Parser();
  }

  public Parser withResponseFormat(ResponseFormatType responseFormat) {
    this.responseFormat = responseFormat;
    return this;
  }

  public Parser toolCallingEnabled(boolean toolCallingEnabled) {
    this.toolCallingEnabled = toolCallingEnabled;
    return this;
  }

  public Parser parseToolCallsFromText(boolean parseToolCallsFromText) {
    this.parseToolCallsFromText = parseToolCallsFromText;
    return this;
  }

  public Parser parseThoughtsFromText(boolean parseThoughtsFromText) {
    this.parseThoughtsFromText = parseThoughtsFromText;
    return this;
  }

  public Parser areThoughtsEnabled(boolean thoughtsEnabled) {
    this.thoughtsEnabled = thoughtsEnabled;
    return this;
  }

  public Parser withThoughtsStartTag(String thoughtsStartTag) {
    this.thoughtsStartTag = thoughtsStartTag;
    return this;
  }

  public Parser withThoughtsEndTag(String thoughtsEndTag) {
    this.thoughtsEndTag = thoughtsEndTag;
    return this;
  }

  public Content parse(Content content) {
    if (content == null) {
      return null;
    }

    if (responseFormat == ResponseFormatType.JSON) {
      return parseJsonContent(content);
    } else {
      return parseTextContent(content);
    }
  }

  private Content parseJsonContent(final Content content) {
    final Content parsed = buildContentFromJsonText(content);
    if (parsed == null) {
      return content;
    }
    final List<Part> toolCallParts = CollectionUtils.nullSafeList(toolCallingEnabled ? (parseToolCallsFromText ? getToolCallParts(parsed) : getToolCallParts(content)) : List.of());
    final List<Part> otherParts = content.parts().orElse(Collections.emptyList()).stream().filter(part -> part.functionCall().orElse(null) == null).toList();
    final List<Part> allParts = new ArrayList<>(toolCallParts);
    allParts.addAll(otherParts);
    if (parseThoughtsFromText) {
      allParts.addAll(parsed.parts().orElse(List.of()).stream().filter(part -> part.thought().orElse(false)).toList());
    }
    return Content.builder().role(content.role().orElse(null)).parts(allParts).build();
  }

  @NotNull
  private static List<Part> getToolCallParts(final Content content) {
    return content.parts().orElse(Collections.emptyList()).stream().filter(part -> part.functionCall().orElse(null) != null).toList();
  }

  private Content parseTextContent(Content content) {
    final String text = content.text();
    final String processedText = stripThoughtBlock(text);
    final String thoughts = getThoughts(text);
    List<Part> toolCallParts = toolCallingEnabled ? getToolCallParts(content) : List.of();
    if (toolCallingEnabled && parseToolCallsFromText) {
      final List<ToolCall> toolCalls = parseToolCalls(processedText);
      toolCallParts = toolCalls.stream().map(Parser::buildToolCallPart).toList();
    }

    final String finalAnswer = processedText == null ? "" : processedText.trim();
    final List<Part> allParts = new ArrayList<>(toolCallParts);
    allParts.add(Part.builder().text(finalAnswer).build());
    if (parseThoughtsFromText && StringUtils.isNotBlank(thoughts)) {
      allParts.add(Part.builder().text(thoughts).thought(true).build());
    } else if (!parseThoughtsFromText) {
      allParts.addAll(content.parts().orElse(List.of()).stream().filter(part -> part.thought().orElse(false)).toList());
    }
    return Content.builder().role(content.role().orElse(null)).parts(allParts).build();
  }

  private static Part buildToolCallPart(final ToolCall toolCall) {
    return Part.builder().functionCall(FunctionCall.builder().id(toolCall.id()).name(toolCall.name()).args(toolCall.args())).build();
  }

  private String getThoughts(String content) {
        if (StringUtils.isBlank(content)
                || !thoughtsEnabled || !parseThoughtsFromText
                || StringUtils.isBlank(thoughtsStartTag)
                || StringUtils.isBlank(thoughtsEndTag)) {
            return null;
        }
        final Pattern thoughtPattern =
                Pattern.compile(
                        STR."\{Pattern.quote(thoughtsStartTag)}(.*?)\{Pattern.quote(thoughtsEndTag)}",
                        Pattern.DOTALL);
        final Matcher matcher = thoughtPattern.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        final String thoughts = matcher.group(1);
        return thoughts == null ? null : thoughts.trim();
    }

  private Content buildContentFromJsonText(final Content content) {
    final String text = content.text();
    final Map<String, Object> payload = parseJsonPayload(text);
    if (payload == null) {
      return null;
    }
    final String finalAnswer = CollectionUtils.getStringValueFromMap(payload, FINAL_ANSWER_KEY);
    final String thoughts = thoughtsEnabled ? CollectionUtils.getStringValueFromMap(payload, THOUGHTS_KEY) : null;
    final List<ToolCall> toolCalls = toolCallingEnabled && parseToolCallsFromText
        ? parseToolCallsFromJsonMap(payload)
        : List.of();
    final Part finalAnswerPart = Part.builder().text(finalAnswer).build();
    final Part thoughtsPart = Part.builder().text(thoughts).thought(true).build();
    final List<Part> toolCallParts = toolCalls.stream().map(Parser::buildToolCallPart).toList();
    final List<Part> allParts = new ArrayList<>();
    allParts.add(finalAnswerPart);
    allParts.add(thoughtsPart);
    allParts.addAll(toolCallParts);
    return Content.builder().role(content.role().orElse(null)).parts(allParts).build();
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
      final Object argsValue = map.get("args");
      @SuppressWarnings("unchecked")
      final Map<String, Object> args = argsValue instanceof Map<?, ?> argsMap
          ? (Map<String, Object>) argsMap
          : Map.of();
      final Object idValue = map.get("id");
      calls.add(new ToolCall(idValue == null ? null : idValue.toString(), nameValue.toString(), args));
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
        args = JsonUtils.fromJson(argsStr, new TypeReference<>() {
        });
      } catch (Exception e) {
        LOG.warn("Failed to parse tool call arguments: {}", argsStr, e);
        args = Map.of();
      }

      toolCalls.add(new ToolCall(id, name, args));
    }

    return toolCalls.isEmpty() ? List.of() : toolCalls;
  }

  private String stripThoughtBlock(final String text) {
        if (StringUtils.isBlank(text) || !thoughtsEnabled || !parseThoughtsFromText) {
            return text;
        }
        return text.replaceAll(STR."\{Pattern.quote(thoughtsStartTag)}.*?\{Pattern.quote(thoughtsEndTag)}", "").trim();
    }

  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
    final boolean isPartial = response.partial().orElse(false);
    final boolean turnCompleted = response.turnComplete().orElse(false);

    final LlmResponse.Builder builder = response.toBuilder();
    if (!isPartial && turnCompleted) {
        response.content().ifPresent(content -> {
          builder.content(parse(content));
        });
    }

    return Single.just(ResponseProcessingResult.create(builder.build(), List.of(), Optional.empty()));
  }

  @Override
  public Single<RequestProcessingResult> processRequest(final InvocationContext context, final LlmRequest request) {
    final List<Content> contents = new ArrayList<>();

    for (final Content content : CollectionUtils.nullSafeList(request.contents())) {
      List<Part> parts = new ArrayList<>();
      final List<Part> thoughtsParts = new ArrayList<>();
      final List<Part> toolCallParts = new ArrayList<>();
      final List<Part> toolResponseParts = new ArrayList<>();
      final List<Part> regularParts = new ArrayList<>();

      for (final Part part : content.parts().orElse(List.of())) {
        final Optional<FunctionResponse> functionResponse = part.functionResponse();
        final Optional<FunctionCall> functionCall = part.functionCall();

        if (functionResponse != null && functionResponse.isPresent() && toolCallingEnabled && parseToolCallsFromText) {
          toolResponseParts.add(part);
        } else if (functionCall != null && functionCall.isPresent() && toolCallingEnabled && parseToolCallsFromText) {
          toolCallParts.add(part);
        } else if (part.thought().orElse(false) && thoughtsEnabled && parseThoughtsFromText) {
          thoughtsParts.add(part);
        } else {
          regularParts.add(part);
        }
      }

      if (responseFormat == ResponseFormatType.JSON) {
        parts.addAll(buildJsonFormatContent(regularParts, thoughtsParts, toolCallParts));
      } else {
        parts.addAll(buildTextFormatContent(regularParts, thoughtsParts, toolCallParts));
      }

      contents.add(content.toBuilder().parts(parts).build());

      final List<Part> parsedResponseParts = new ArrayList<>();
      if (CollectionUtils.isNotEmpty(toolResponseParts)) {
        for (Part part : toolResponseParts) {
          FunctionResponse response = part.functionResponse().orElse(null);
          if (response == null) {
            continue;
          }
          String responseText = String.format(
                  "Tool Response [%s]: %s",
                  response.name().orElse("unknown"),
                  response.response()
          );
          parsedResponseParts.add(Part.builder().text(responseText).build());
        }
      }

      contents.add(Content.builder().role("user").parts(parsedResponseParts).build());
    }

    return Single.just(RequestProcessingResult.create(request.toBuilder().contents(contents).build(), List.of()));
  }

  private List<Part> buildJsonFormatContent(List<Part> regularParts, List<Part> thoughtsParts, List<Part> toolCallParts) {

    List<Part> result = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(regularParts) ||
            CollectionUtils.isNotEmpty(thoughtsParts) ||
            CollectionUtils.isNotEmpty(toolCallParts)) {

      Map<String, Object> jsonMap = new LinkedHashMap<>();

      if (CollectionUtils.isNotEmpty(regularParts)) {
        StringBuilder answerBuilder = new StringBuilder();
        for (Part part : regularParts) {
          if (part.text().isPresent()) {
            answerBuilder.append(part.text().get()).append(" ");
          }
        }
        jsonMap.put(FINAL_ANSWER_KEY, answerBuilder.toString().trim());
      }

      if (CollectionUtils.isNotEmpty(thoughtsParts)) {
        StringBuilder thoughtsBuilder = new StringBuilder();
        for (Part part : thoughtsParts) {
          if (part.text().isPresent()) {
            thoughtsBuilder.append(part.text().get()).append("\n");
          }
        }
        jsonMap.put(THOUGHTS_KEY, thoughtsBuilder.toString().trim());
      }

      if (CollectionUtils.isNotEmpty(toolCallParts)) {
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
        jsonMap.put("toolCalls", toolCallsList);
      }

      String jsonText = JsonUtils.toJson(jsonMap);
      result.add(Part.builder().text(jsonText).build());
    }

    return result;
  }

  private List<Part> buildTextFormatContent(List<Part> regularParts, List<Part> thoughtsParts, List<Part> toolCallParts) {

    List<Part> result = new ArrayList<>();

    StringBuilder textBuilder = new StringBuilder();

    // Add thoughts at the beginning
    if (CollectionUtils.isNotEmpty(thoughtsParts) && thoughtsEnabled) {
      textBuilder.append(thoughtsStartTag).append("\n");
      for (Part part : thoughtsParts) {
        if (part.text().isPresent()) {
          textBuilder.append(part.text().get()).append("\n");
        }
      }
      textBuilder.append(thoughtsEndTag).append("\n\n");
    }

    // Add regular text parts
    if (CollectionUtils.isNotEmpty(regularParts)) {
      for (Part part : regularParts) {
        if (part.text().isPresent()) {
          textBuilder.append(part.text().get()).append(" ");
        }
      }
    }

    if (CollectionUtils.isNotEmpty(toolCallParts) && toolCallingEnabled) {
      for (Part part : toolCallParts) {
        FunctionCall call = part.functionCall().orElse(null);
        if (call == null) {
          continue;
        }
        String argsJson = JsonUtils.toJson(call.args());
        String toolCallText = String.format(
                "\n{'id': '%s', 'name': '%s', 'args': %s}",
                call.id().orElse(""),
                call.name().orElse(""),
                argsJson
        );
        textBuilder.append(toolCallText);
      }
    }

    String finalText = textBuilder.toString().trim();
    if (StringUtils.isNotBlank(finalText)) {
      result.add(Part.builder().text(finalText).build());
    }

    return result;
  }
}
