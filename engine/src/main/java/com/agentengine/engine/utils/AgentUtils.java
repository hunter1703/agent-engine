package com.agentengine.engine.utils;

import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.TemplateUtils;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.PlanItem;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.beans.session.PlanUpdate;
import com.alibaba.fastjson2.TypeReference;
import com.agentengine.engine.api.utils.StringUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentUtils {
  private static final String UPDATE_PLAN_TOOL_NAME = "update_plan";

  private static final Pattern LIST_ITEM = Pattern.compile("^(?:[-*]|\\d+\\.)\\s*(.+)$");
  private static final Pattern STATUS_PREFIX = Pattern.compile("^\\[(pending|in_progress|completed)]\\s*(.+)$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ID_PREFIX = Pattern.compile("^(?:\\(id:|id=)([^)\\s]+)\\)?\\s*(.+)$",
      Pattern.CASE_INSENSITIVE);

  private AgentUtils() {
  }

  public static Message sanitizeMessage(final Message message, final ResponseFormatType format,
      final boolean thoughtsEnabled, final String thoughtsStartTag, final String thoughtsEndTag) {
    if (message == null) {
      return null;
    }
    final String content = message.getContent();
    if (format == ResponseFormatType.JSON) {
      final Message parsed = buildMessageFromJsonPayload(content);
      if (parsed == null) {
        return new Message(message.getRole(), "", "", List.of());
      }
      return new Message(message.getRole(), parsed.getContent(), parsed.getThoughts(),
          CollectionUtils.nullSafeList(parsed.getToolCalls()));
    }
    final String cleaned = stripThoughtBlock(content, thoughtsEnabled, thoughtsStartTag, thoughtsEndTag);
    final String finalAnswer = cleaned == null ? "" : cleaned.trim();
    final String thoughts = getThoughts(content, thoughtsEnabled, thoughtsStartTag, thoughtsEndTag);
    final List<ToolCall> toolCalls = new ArrayList<>(CollectionUtils.nullSafeList(message.getToolCalls()));
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
    final java.util.Set<String> seenIds = new java.util.HashSet<>();
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
    final boolean invalid = finalAnswerAndToolCallsPresent || emptyResponse || missingToolCallId
        || missingToolCallName || duplicateToolCallId;
    if (!invalid) {
      return null;
    }
    return TemplateUtils.renderTemplateForName("hybrid/repair/invalid_message.txt",
        Map.of("finalAnswerAndToolCallsPresent", finalAnswerAndToolCallsPresent, "emptyResponse", emptyResponse,
            "missingToolCallId", missingToolCallId, "missingToolCallName", missingToolCallName, "duplicateToolCallId",
            duplicateToolCallId));
  }

  public static List<PlanItem> parsePlanItemsFromText(final String text) {
    if (StringUtils.isBlank(text)) {
      return List.of();
    }
    final List<PlanItem> items = new ArrayList<>();
    int index = 0;
    for (String line : text.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      final var matcher = LIST_ITEM.matcher(trimmed);
      if (!matcher.matches()) {
        continue;
      }
      trimmed = matcher.group(1).trim();
      if (StringUtils.isBlank(trimmed)) {
        continue;
      }
      PlanStatus status = PlanStatus.PENDING;
      final Matcher statusMatcher = STATUS_PREFIX.matcher(trimmed);
      if (statusMatcher.matches()) {
        status = PlanStatus.fromString(statusMatcher.group(1));
        trimmed = statusMatcher.group(2).trim();
      }
      String id = null;
      final var idMatcher = ID_PREFIX.matcher(trimmed);
      if (idMatcher.matches()) {
        id = idMatcher.group(1);
        trimmed = idMatcher.group(2).trim();
      }
      if (StringUtils.isBlank(trimmed)) {
        continue;
      }
      index++;
      final String resolvedId = StringUtils.isBlank(id) ? derivePlanId(trimmed, index) : id;
      items.add(new PlanItem(resolvedId, trimmed, status));
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

  public static PlanUpdate parsePlanUpdate(final List<ToolCall> toolCalls) {
    if (CollectionUtils.isEmpty(toolCalls)) {
      return null;
    }
    for (ToolCall call : toolCalls) {
      if (call == null || call.args() == null || call.name() == null) {
        continue;
      }
      if (!UPDATE_PLAN_TOOL_NAME.equalsIgnoreCase(call.name())) {
        continue;
      }
      final Map<String, Object> args = call.args();
      final String explanation = CollectionUtils.getStringValueFromMap(args, "explanation");
      final Object rawItems = args.get("plan");
      final List<PlanItem> items = parsePlanItems(rawItems);
      return new PlanUpdate(explanation, items);
    }
    return null;
  }

  public static Message buildMessageFromJsonPayload(final String text) {
    final Map<String, Object> payload = parseJsonPayload(text);
    if (payload == null) {
      return null;
    }
    final String finalAnswer = CollectionUtils.getStringValueFromMap(payload, "finalAnswer");
    final String thoughts = CollectionUtils.getStringValueFromMap(payload, "thoughts");
    final List<ToolCall> toolCalls = parseToolCallsFromJsonMap(payload);
    final String content = finalAnswer == null ? "" : finalAnswer;
    return new Message(null, content, thoughts, toolCalls);
  }
  public static Map<String, Object> parseJsonPayload(final String text) {
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
    return payload;
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

  private static List<PlanItem> parsePlanItems(final Object rawItems) {
    if (!(rawItems instanceof List<?> list)) {
      return List.of();
    }
    final List<PlanItem> items = new ArrayList<>();
    int index = 0;
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> rawMap)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      final Map<String, Object> map = (Map<String, Object>) rawMap;
      final String step = CollectionUtils.getStringValueFromMap(map, "step");
      if (StringUtils.isBlank(step)) {
        continue;
      }
      index++;
      final String id = CollectionUtils.getStringValueFromMap(map, "id");
      final String resolvedId = StringUtils.isBlank(id) ? derivePlanId(step, index) : id;
      final PlanStatus status = PlanStatus.fromString(CollectionUtils.getStringValueFromMap(map, "status"));
      items.add(new PlanItem(resolvedId, step, status));
    }
    return items;
  }

  private static String derivePlanId(final String step, final int index) {
    if (StringUtils.isBlank(step)) {
      return STR."step-\{index}";
    }
    final String normalized = step.toLowerCase()
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
    if (StringUtils.isBlank(normalized)) {
      return STR."step-\{index}";
    }
    final String trimmed = normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    return STR."\{trimmed}-\{index}";
  }

}
