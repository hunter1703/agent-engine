package com.agentengine.engine.api.state;

import com.agentengine.engine.api.beans.session.Summary;
import com.agentengine.engine.api.beans.session.Message;

import java.util.List;

public interface SessionStore {

  List<Message> getMessages(String sessionId);

  String appendMessage(String sessionId, Message message);

  void updateMessage(String sessionId, String messageId, Message message);

  List<Summary> getSummaries(String sessionId);

  void addSummary(String sessionId, String summarizedFromMessageId, String summarizedUptoMessageId, String summary,
      long createdAt);
}
