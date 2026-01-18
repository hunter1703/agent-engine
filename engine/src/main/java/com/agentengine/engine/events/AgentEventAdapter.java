package com.agentengine.engine.events;

import com.agentengine.engine.AgentListener;
import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.ToolCall;
import com.agentengine.engine.tools.ToolRegistry;
import com.agentengine.engine.utils.CollectionUtils;
import com.agentengine.engine.utils.JsonUtils;
import com.agentengine.engine.utils.ToolRequest;

import javax.tools.Tool;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AgentEventAdapter implements AgentListener {
  private final AgentEventPublisher publisher;

  public AgentEventAdapter(final AgentEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void onToolPlan(final String sessionId, final List<ToolCall> toolCalls) {
    List<Map<String, Object>> payload = toolCalls.stream()
        .map(call -> Map.of("id", call.id(), "name", call.name(), "args", call.args())).toList();
    publisher.publish(new AgentEvent("tool_plan", sessionId, payload));
  }

  @Override
  public void onToolExecution(final String sessionId, final ToolExecution toolExecution) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("id", toolExecution.getId());
    payload.put("tool_name", toolExecution.getToolCall().name());
    payload.put("status", toolExecution.getStatus());
    payload.put("output", toolExecution.getOutput());
    payload.put("duration_ms", toolExecution.getDurationMs());
    publisher.publish(new AgentEvent("tool_execution", sessionId, payload));
  }

  @Override
  public void onReasoningStart(final String sessionId) {
    publisher.publish(new AgentEvent("reasoning", sessionId, Map.of("status", "start")));
  }

  @Override
  public void onReasoningEnd(final String sessionId, final Message message) {
    publisher.publish(new AgentEvent("reasoning", sessionId, Map.of("status", "end", "responseContent", message.getContent(), "responseThoughts", message.getThoughts())));
  }

  @Override
  public void onToolRepair(final String sessionId, final List<ToolCall> toolCalls, final List<ToolRequest> missingToolRequests) {
    publisher.publish(new AgentEvent("tool", sessionId, Map.of("status", "repair", "toolCalls", JsonUtils.toJson(toolCalls), "remainingToolRequests", CollectionUtils.isEmpty(missingToolRequests) ? "" : JsonUtils.toJson(missingToolRequests))));
  }

  @Override
  public void onFinalAnswer(final String sessionId, final Message message) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("final_answer", message.getContent());
    payload.put("thoughts", message.getThoughts());
    publisher.publish(new AgentEvent("final_answer", sessionId, payload));
  }
}
