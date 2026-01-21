package com.agentengine.engine.events;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.commons.utils.CollectionUtils;
import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolRequest;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AgentEventAdapter implements AgentListener {
  private final AgentEventPublisher publisher;

  public AgentEventAdapter(final AgentEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void onToolPlan(final String sessionId, final Collection<ToolCall> toolCalls) {
    List<Map<String, Object>> payload = toolCalls.stream()
        .map(call -> Map.of("id", call.id(), "name", call.name(), "args", call.args())).toList();
    publisher.publish(new AgentEvent("tool_plan", sessionId, payload));
  }

  @Override
  public void onRunStarted(String sessionId, String runId) {
    publisher.publish(new AgentEvent("run_started", sessionId, Map.of("runId", runId)));
  }

  @Override
  public void onRunFinished(String sessionId, String runId) {
    publisher.publish(new AgentEvent("run_finished", sessionId, Map.of("runId", runId)));
  }

  @Override
  public void onTextMessageStart(String sessionId, String messageId, String role) {
    publisher.publish(new AgentEvent("text_message_start", sessionId, Map.of("messageId", messageId, "role", role)));
  }

  @Override
  public void onTextMessageDelta(String sessionId, String messageId, String delta) {
    publisher.publish(new AgentEvent("text_message_delta", sessionId, Map.of("messageId", messageId, "delta", delta)));
  }

  @Override
  public void onTextMessageEnd(String sessionId, String messageId) {
    publisher.publish(new AgentEvent("text_message_end", sessionId, Map.of("messageId", messageId)));
  }

  @Override
  public void onStepStarted(String sessionId, String stepName) {
    publisher.publish(new AgentEvent("step_started", sessionId, Map.of("stepName", stepName)));
  }

  @Override
  public void onStepFinished(String sessionId, String stepName) {
    publisher.publish(new AgentEvent("step_finished", sessionId, Map.of("stepName", stepName)));
  }

  @Override
  public void onToolCallStart(String sessionId, String toolCallId, String toolCallName) {
    publisher.publish(
        new AgentEvent("tool_call_start", sessionId, Map.of("toolCallId", toolCallId, "toolCallName", toolCallName)));
  }

  @Override
  public void onToolCallArgs(String sessionId, String toolCallId, String delta) {
    publisher.publish(new AgentEvent("tool_call_args", sessionId, Map.of("toolCallId", toolCallId, "delta", delta)));
  }

  @Override
  public void onToolCallEnd(String sessionId, String toolCallId) {
    publisher.publish(new AgentEvent("tool_call_end", sessionId, Map.of("toolCallId", toolCallId)));
  }

  @Override
  public void onToolCallResult(String sessionId, String toolCallId, String content) {
    publisher
        .publish(new AgentEvent("tool_call_result", sessionId, Map.of("toolCallId", toolCallId, "result", content)));
  }

  @Override
  public void onReasoningMessageStart(String sessionId, String messageId, String role) {
    publisher
        .publish(new AgentEvent("reasoning_message_start", sessionId, Map.of("messageId", messageId, "role", role)));
  }

  @Override
  public void onReasoningMessageDelta(String sessionId, String messageId, String delta) {
    publisher
        .publish(new AgentEvent("reasoning_message_delta", sessionId, Map.of("messageId", messageId, "delta", delta)));
  }

  @Override
  public void onReasoningMessageEnd(String sessionId, String messageId) {
    publisher.publish(new AgentEvent("reasoning_message_end", sessionId, Map.of("messageId", messageId)));
  }

  @Override
  public void onToolRepair(final String sessionId, final List<ToolCall> toolCalls,
      final List<ToolRequest> missingToolRequests) {
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
