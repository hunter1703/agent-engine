package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.ToolExecution;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.ToolCall;
import com.agentengine.engine.api.beans.session.ToolRequest;

import java.util.Collection;
import java.util.List;

public interface AgentListener extends EngineListener {
  default void onRunStarted(String sessionId, String runId) {
  }

  default void onRunFinished(String sessionId, String runId) {
  }

  default void onTextMessageStart(String sessionId, String messageId, String role) {
  }

  default void onTextMessageDelta(String sessionId, String messageId, String delta) {
  }

  default void onTextMessageEnd(String sessionId, String messageId) {
  }

  default void onStepStarted(String sessionId, String stepName) {
  }

  default void onStepFinished(String sessionId, String stepName) {
  }

  default void onToolCallStart(String sessionId, String toolCallId, String toolCallName) {
  }

  default void onToolCallArgs(String sessionId, String toolCallId, String delta) {
  }

  default void onToolCallEnd(String sessionId, String toolCallId) {
  }

  default void onToolCallResult(String sessionId, String toolCallId, String content) {
  }

  default void onReasoningMessageStart(String sessionId, String messageId, String role) {
  }

  default void onReasoningMessageDelta(String sessionId, String messageId, String delta) {
  }

  default void onReasoningMessageEnd(String sessionId, String messageId) {
  }

  default void onToolPlan(String sessionId, Collection<ToolCall> toolCalls) {
  }

  default void onToolRepair(String sessionId, List<ToolCall> toolCalls, List<ToolRequest> remainingRequests) {
  }

  void onFinalAnswer(String sessionId, Message message);
}
