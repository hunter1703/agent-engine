package com.agentengine.engine.events;

import com.agentengine.engine.api.AgentListener;
import com.agentengine.engine.api.beans.session.PlanUpdate;
import java.util.HashMap;
import java.util.Map;

public final class AgentEventAdapter implements AgentListener {
  private final AgentEventPublisher publisher;

  public AgentEventAdapter(final AgentEventPublisher publisher) {
    this.publisher = publisher;
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
  public void onThinkingMessageStart(String sessionId, String messageId, String role) {
    publisher
        .publish(new AgentEvent("thinking_message_start", sessionId, Map.of("messageId", messageId, "role", role)));
  }

  @Override
  public void onThinkingMessageDelta(String sessionId, String messageId, String delta) {
    publisher
        .publish(new AgentEvent("thinking_message_delta", sessionId, Map.of("messageId", messageId, "delta", delta)));
  }

  @Override
  public void onThinkingMessageEnd(String sessionId, String messageId) {
    publisher.publish(new AgentEvent("thinking_message_end", sessionId, Map.of("messageId", messageId)));
  }

  @Override
  public void onPlanUpdate(final String sessionId, final PlanUpdate update) {
    if (update == null) {
      return;
    }
    final Map<String, Object> payload = new HashMap<>();
    payload.put("explanation", update.explanation());
    payload.put("plan", update.plan() == null ? null : update.plan().stream()
        .filter(item -> item != null && item.step() != null)
        .map(item -> Map.of("step", item.step(), "status", item.status().name().toLowerCase()))
        .toList());
    publisher.publish(new AgentEvent("plan_update", sessionId, payload));
  }
}
