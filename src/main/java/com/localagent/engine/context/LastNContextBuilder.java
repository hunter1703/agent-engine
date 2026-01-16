package com.localagent.engine.context;

import com.localagent.engine.beans.ToolExecution;
import com.localagent.engine.message.Message;
import com.localagent.engine.state.SessionStore;
import com.localagent.engine.tools.AgentTool;
import com.localagent.engine.utils.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class LastNContextBuilder extends BaseContextBuilder {
  private final int keepLast;

  public LastNContextBuilder(
      final SessionStore sessionStore,
      final String systemMessage,
      final String protocolMessage,
      final List<AgentTool> tools,
      final int keepLast) {
    super(sessionStore, systemMessage, protocolMessage, tools);
    this.keepLast = Math.max(1, keepLast);
  }

  @Override
  public List<Message> buildPrompt(final String sessionId) {
    final List<Message> messages = sessionStore.getMessages(sessionId);

    final List<Message> recent =
        messages.size() <= keepLast
            ? messages
            : messages.subList(messages.size() - keepLast, messages.size());
    final Map<String, List<ToolExecution>> messageIdVsToolExecutions =
        sessionStore.getToolExecutions(
            sessionId, recent.stream().map(Message::getId).collect(Collectors.toList()));

    final List<Message> toBuildWith = new ArrayList<>();
    for (final Message message : recent) {
      toBuildWith.add(message);
      final List<ToolExecution> toolExecutions = messageIdVsToolExecutions.get(message.getId());
      if (CollectionUtils.isNotEmpty(toolExecutions)) {
        toolExecutions.forEach(toolExecution -> toBuildWith.add(toolExecution.toMessage()));
      }
    }

    return super.buildPrompt(toBuildWith);
  }
}
