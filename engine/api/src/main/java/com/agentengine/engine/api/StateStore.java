package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.Summary;
import com.agentengine.engine.api.beans.session.Message;

import java.util.List;

public interface StateStore {

  List<Message> getMessages(String sessionId);

  String appendMessage(String sessionId, String runId, Message message);

  void updateMessage(String sessionId, String messageId, Message message);

  List<Summary> getSummaries(String sessionId);

  void addSummary(String sessionId, String summarizedFromMessageId, String summarizedUptoMessageId, String summary,
      long createdAt);
}
