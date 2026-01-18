package com.agentengine.engine.events;

import com.agentengine.engine.AgentListener;
import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.ToolCall;
import com.agentengine.engine.utils.CollectionUtils;
import com.agentengine.engine.utils.ToolRequest;
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
    publisher.publish(new AgentEvent("reasoning_start", sessionId, Map.of("status", "start")));
  }

  @Override
  public void onReasoningEnd(final String sessionId, final Message message) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("status", "end");
    if (message != null) {
      payload.put("responseContent", message.getContent());
      payload.put("responseThoughts", message.getThoughts());
    }
    publisher.publish(new AgentEvent("reasoning_end", sessionId, payload));
  }

  @Override
  public void onToolRepair(final String sessionId, final List<ToolCall> toolCalls, final List<ToolRequest> missingToolRequests) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("status", "repair");
    payload.put("toolCalls", CollectionUtils.nullSafeList(toolCalls));
    payload.put("remainingToolRequests", CollectionUtils.nullSafeList(missingToolRequests));
    publisher.publish(new AgentEvent("tool_repair", sessionId, payload));
  }

  @Override
  public void onFinalAnswer(final String sessionId, final Message message) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("final_answer", message.getContent());
    payload.put("thoughts", message.getThoughts());
    publisher.publish(new AgentEvent("final_answer", sessionId, payload));
  }
}
