package com.agentengine.engine.api;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.beans.ToolContext;
import com.agentengine.engine.api.beans.ToolResult;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.tools.Tool;
import java.util.Map;
import java.util.Objects;

import java.util.List;

public interface Agent extends Tool {

  AgentListener NO_OP_LISTENER = new AgentListener() {
  };

  Message invoke(String sessionId, Message message, AgentListener listener);

  List<Message> buildPrompt(String sessionId);

  @Override
  default String name() {
    return getClass().getSimpleName();
  }

  @Override
  default String description() {
    return STR."Agent tool: \{name()}";
  }

  @Override
  default String execute(final Map<String, Object> args) {
    final ToolResult result = executeWithContext(new ToolContext("tool"), args);
    return result == null ? null : result.output();
  }

  @Override
  default ToolResult executeWithContext(final ToolContext context, final Map<String, Object> args) {
    final String sessionId = context == null ? "tool" : context.sessionId();
    final String message = extractMessage(args);
    final AgentToolListener listener = new AgentToolListener();
    invoke(sessionId, Message.user(message), listener);
    return ToolResult.ok(listener.finalAnswer());
  }

  private static String extractMessage(final Map<String, Object> args) {
    if (args == null || args.isEmpty()) {
      return "";
    }
    for (String key : List.of("message", "input", "content", "text")) {
      Object value = args.get(key);
      if (value != null) {
        return value.toString();
      }
    }
    return "";
  }

  final class AgentToolListener implements AgentListener {
    private String finalAnswer = "";
    private String currentMessageId;
    private StringBuilder currentMessage;
    private boolean capturingAssistant;

    @Override
    public void onTextMessageStart(final String sessionId, final String messageId, final String role) {
      capturingAssistant = "assistant".equalsIgnoreCase(role);
      if (capturingAssistant) {
        currentMessageId = messageId;
        currentMessage = new StringBuilder();
      } else {
        currentMessageId = null;
        currentMessage = null;
      }
    }

    @Override
    public void onTextMessageDelta(final String sessionId, final String messageId, final String delta) {
      if (!capturingAssistant || currentMessage == null || !Objects.equals(messageId, currentMessageId)) {
        return;
      }
      if (delta != null) {
        currentMessage.append(delta);
      }
    }

    @Override
    public void onTextMessageEnd(final String sessionId, final String messageId) {
      if (!capturingAssistant || currentMessage == null || !Objects.equals(messageId, currentMessageId)) {
        return;
      }
      finalAnswer = currentMessage.toString();
      currentMessage = null;
      currentMessageId = null;
      capturingAssistant = false;
    }

    private String finalAnswer() {
      return finalAnswer;
    }
  }
}
