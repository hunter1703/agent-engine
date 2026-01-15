package com.localagent.engine.state;

import com.localagent.engine.message.Message;
import java.util.List;

public interface SessionStore {

    List<Message> getMessages(String sessionId);

    String appendMessage(String sessionId, Message message);

    List<ToolExecution> getToolExecutions(String sessionId, String messageId);

    List<Summary> getSummaries(String sessionId, String agent);

    void addSummary(String sessionId, String summarizedFromMessageId, String summarizedUptoMessageId, String summary, long createdAt);
}
