package com.agentengine.engine.utils;

import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.TemplateUtils;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.StateStore;
import com.agentengine.engine.api.beans.session.PlanItem;
import com.alibaba.fastjson2.TypeReference;
import com.agentengine.engine.api.utils.StringUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EngineUtils {
  private static final String PLAN_TOOL_NAME = "plan_items";
  private static final String LEGACY_PLAN_TOOL_NAME = "plan";

  private static final Pattern FINAL_BLOCK = Pattern.compile("FINAL:\\s*(.*)$",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern PLAN_BLOCK = Pattern.compile(
      "PLAN:\\s*(.*?)(?=\\n\\s*FINAL:|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private EngineUtils() {
  }

  public static int invocationsThisTurn(final StateStore stateStore, final String sessionId) {
    final List<Message> messages = stateStore.getMessages(sessionId);
    int count = 0;
    for (int i = messages.size() - 1; i >= 0; i--) {
      final Message message = messages.get(i);
      if (message.getRole() == Role.USER) {
        break;
      }
      if (message.getRole() == Role.ASSISTANT) {
        count++;
      }
    }
    return count;
  }

  public static Message sanitizeMessage(final Message message, final ResponseFormatType format,
      final boolean thoughtsEnabled, final String thoughtsStartTag, final String thoughtsEndTag) {
    if (message == null) {
      return null;
    }
    final String content = message.getContent();
    if (format == ResponseFormatType.JSON) {
      final Message parsed = parseJsonPayload(content);
      if (parsed == null) {
        return new Message(message.getRole(), "", "", List.of());
      }
      return new Message(message.getRole(), parsed.getContent(), parsed.getThoughts(),
          CollectionUtils.nullSafeList(parsed.getToolCalls()));
    }
    final String cleaned = stripThoughtBlock(content, thoughtsEnabled, thoughtsStartTag, thoughtsEndTag);
    final List<PlanItem> planItems = parsePlanItemsFromText(cleaned);
    final String finalAnswer = planItems.isEmpty() ? getFinalAnswer(cleaned) : null;
    final String thoughts = getThoughts(content, thoughtsEnabled, thoughtsStartTag, thoughtsEndTag);
    final List<ToolCall> toolCalls = new ArrayList<>(CollectionUtils.nullSafeList(message.getToolCalls()));
    if (!planItems.isEmpty()) {
      toolCalls.add(new ToolCall(UUID.randomUUID().toString(), PLAN_TOOL_NAME, Map.of("items", planItems.stream()
          .map(item -> Map.of("id", item.id(), "description", item.description()))
          .toList())));
    }
    return new Message(message.getRole(), finalAnswer, thoughts, toolCalls);
  }

  public static String getRepairMessageIfInvalid(final Message message) {
    final String content = message.getContent();
    final List<ToolCall> toolCalls = CollectionUtils.nullSafeList(message.getToolCalls());
    final String thoughts = message.getThoughts();

    final boolean finalAnswerAndToolCallsPresent = StringUtils.isNotBlank(content)
        && CollectionUtils.isNotEmpty(toolCalls);
    final boolean emptyResponse = StringUtils.isBlank(content) && StringUtils.isBlank(thoughts)
        && CollectionUtils.isEmpty(toolCalls);
    boolean missingToolCallId = false;
    boolean missingToolCallName = false;
    boolean duplicateToolCallId = false;
    final Set<String> seenIds = new HashSet<>();
    for (ToolCall call : toolCalls) {
      if (StringUtils.isBlank(call.id())) {
        missingToolCallId = true;
      } else if (!seenIds.add(call.id())) {
        duplicateToolCallId = true;
      }
      if (StringUtils.isBlank(call.name())) {
        missingToolCallName = true;
      }
    }
    return TemplateUtils.renderTemplateForName("hybrid/repair/invalid_message.txt",
        Map.of("finalAnswerAndToolCallsPresent", finalAnswerAndToolCallsPresent, "emptyResponse", emptyResponse,
            "missingToolCallId", missingToolCallId, "missingToolCallName", missingToolCallName,
            "duplicateToolCallId", duplicateToolCallId));
  }

  private static String getFinalAnswer(final String cleaned) {
    if (StringUtils.isBlank(cleaned)) {
      return cleaned;
    }
    final Matcher finalMatch = FINAL_BLOCK.matcher(cleaned);
    return (finalMatch.find() ? finalMatch.group(1) : cleaned).trim();
  }

  private static List<PlanItem> parsePlanItemsFromText(final String cleaned) {
    if (StringUtils.isBlank(cleaned)) {
      return List.of();
    }
    final Matcher matcher = PLAN_BLOCK.matcher(cleaned);
    if (!matcher.find()) {
      return List.of();
    }
    final String planBlock = matcher.group(1) == null ? "" : matcher.group(1);
    final List<PlanItem> items = new ArrayList<>();
    int index = 0;
    for (String line : planBlock.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
        trimmed = trimmed.substring(1).trim();
      }
      if (StringUtils.isBlank(trimmed)) {
        continue;
      }
      index++;
      items.add(new PlanItem(STR."step-\{index}", trimmed));
    }
    return items;
  }

  private static String stripThoughtBlock(
      final String text,
      final boolean thoughtsEnabled,
      final String thoughtsStartTag,
      final String thoughtsEndTag) {
    if (StringUtils.isBlank(text) || !thoughtsEnabled) {
      return text;
    }
    return text.replaceAll(
            STR."\{Pattern.quote(thoughtsStartTag)}.*?\{Pattern.quote(thoughtsEndTag)}", "")
        .trim();
  }

  private static String getThoughts(
      final String content,
      final boolean thoughtsEnabled,
      final String thoughtsStartTag,
      final String thoughtsEndTag) {
    if (StringUtils.isBlank(content)
        || !thoughtsEnabled
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

  public static List<PlanItem> parsePlanItems(final List<ToolCall> toolCalls) {
    if (CollectionUtils.isEmpty(toolCalls)) {
      return List.of();
    }
    final List<PlanItem> items = new ArrayList<>();
    for (ToolCall call : toolCalls) {
      if (call == null || call.args() == null || call.name() == null) {
        continue;
      }
      if (!PLAN_TOOL_NAME.equalsIgnoreCase(call.name()) && !LEGACY_PLAN_TOOL_NAME.equalsIgnoreCase(call.name())) {
        continue;
      }
      final Object rawItems = call.args().getOrDefault("items", call.args().get("steps"));
      if (!(rawItems instanceof List<?> list)) {
        continue;
      }
      int index = 0;
      for (Object item : list) {
        index++;
        String id = null;
        String description = null;
        if (item instanceof Map<?, ?> rawMap) {
          @SuppressWarnings("unchecked")
          final Map<String, Object> map = (Map<String, Object>) rawMap;
          id = CollectionUtils.getStringValueFromMap(map, "id");
          if (id == null) {
            id = CollectionUtils.getStringValueFromMap(map, "step");
          }
          description = CollectionUtils.getStringValueFromMap(map, "description");
          if (description == null) {
            description = CollectionUtils.getStringValueFromMap(map, "task");
          }
          if (description == null) {
            description = CollectionUtils.getStringValueFromMap(map, "title");
          }
        } else if (item != null) {
          description = item.toString();
        }
        if (StringUtils.isBlank(description)) {
          continue;
        }
        if (StringUtils.isBlank(id)) {
          id = STR."step-\{index}";
        }
        items.add(new PlanItem(id, description));
      }
    }
    return items;
  }

  public static Message parseJsonPayload(final String text) {
    if (StringUtils.isBlank(text)) {
      return null;
    }
    String cleaned = text.trim();
    if (cleaned.startsWith("```")) {
      int end = cleaned.lastIndexOf("```");
      if (end > 2) {
        cleaned = cleaned.substring(3, end).trim();
        if (cleaned.startsWith("json")) {
          cleaned = cleaned.substring(4).trim();
        }
      }
    }
    Map<String, Object> payload = null;
    try {
      payload = JsonUtils.fromJson(cleaned, new TypeReference<>() {
      });
    } catch (Exception ex) {
      final int start = cleaned.indexOf('{');
      final int end = cleaned.lastIndexOf('}');
      if (start >= 0 && end > start) {
        try {
          payload = JsonUtils.fromJson(cleaned.substring(start, end + 1), new TypeReference<>() {
          });
        } catch (Exception ignored) {
        }
      }
    }
    if (payload == null) {
      return null;
    }
    final String finalAnswer = CollectionUtils.getStringValueFromMap(payload, "finalAnswer");
    final String thoughts = CollectionUtils.getStringValueFromMap(payload, "thoughts");
    final List<ToolCall> toolCalls = parseToolCallsFromJsonMap(payload);
    final String content = finalAnswer == null ? "" : finalAnswer;
    return new Message(null, content, thoughts, toolCalls);
  }

  public static List<ToolCall> transformToToolCalls(final List<ToolExecutionRequest> requests) {
    final List<ToolCall> toolCalls = new ArrayList<>();
    for (final ToolExecutionRequest request : CollectionUtils.nullSafeList(requests)) {
      final ToolCall toolCall = new ToolCall(request.id(), request.name(),
          JsonUtils.fromJson(request.arguments(), new TypeReference<>() {
          }));
      toolCalls.add(toolCall);
    }
    return toolCalls;
  }

  private static List<ToolCall> parseToolCallsFromJsonMap(final Map<String, Object> payload) {
    final Object toolCallsValue = payload.containsKey("toolCalls") ? payload.get("toolCalls") : payload.get("toolRequests");
    if (!(toolCallsValue instanceof List<?> list)) {
      if (payload.containsKey("tool_name")) {
        Object nameValue = payload.get("tool_name");
        Object argsValue = payload.get("tool_args");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = argsValue instanceof Map<?, ?> argsMap ? (Map<String, Object>) argsMap : Map.of();
        return List.of(new ToolCall(null, nameValue == null ? "" : nameValue.toString(), args));
      }
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

}
