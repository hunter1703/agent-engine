package com.agentengine.engine.state;

import com.agentengine.engine.beans.Summary;
import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.message.Message;
import java.util.List;
import java.util.Map;

public interface SessionStore {

  List<Message> getMessages(String sessionId);

  String appendMessage(String sessionId, Message message);

  void addToolExecutions(String sessionId, String messageId, List<ToolExecution> toolExecutions);

  Map<String, List<ToolExecution>> getToolExecutions(String sessionId, List<String> messageIds);

  List<Summary> getSummaries(String sessionId);

  void addSummary(
      String sessionId,
      String summarizedFromMessageId,
      String summarizedUptoMessageId,
      String summary,
      long createdAt);
}
