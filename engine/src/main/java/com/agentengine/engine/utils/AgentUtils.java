package com.agentengine.engine.utils;

import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.alibaba.fastjson2.TypeReference;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgentUtils {
  private static final Logger LOG = LoggerFactory.getLogger(AgentUtils.class);

  private AgentUtils() {
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
        } catch (Exception innerEx) {
          LOG.warn("Failed to parse JSON payload from substring", innerEx);
        }
      }
    }
    return payload;
  }

  public static List<ToolCall> transformToToolCalls(final List<ToolExecutionRequest> requests) {
    final List<ToolCall> toolCalls = new ArrayList<>();
    if (requests == null) {
      return toolCalls;
    }
    for (final ToolExecutionRequest request : requests) {
      final ToolCall toolCall = new ToolCall(request.id(), request.name(),
          JsonUtils.fromJson(request.arguments(), new TypeReference<>() {
          }));
      toolCalls.add(toolCall);
    }
    return toolCalls;
  }
}
