package com.agentengine.engine.api;

public interface AgentListener {
  default void onRunStarted(String sessionId, String runId) {
  }

  default void onRunFinished(String sessionId, String runId) {
  }

  default void onRunError(String sessionId, String runId, Throwable t) {
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

  default void onThinkingMessageStart(String sessionId, String messageId, String role) {
  }

  default void onThinkingMessageDelta(String sessionId, String messageId, String delta) {
  }

  default void onThinkingMessageEnd(String sessionId, String messageId) {
  }

}
