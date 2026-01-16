package com.localagent.engine.state;

import com.localagent.engine.beans.Summary;
import com.localagent.engine.beans.ToolExecution;
import com.localagent.engine.message.Message;
import com.localagent.engine.utils.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemorySessionStore implements SessionStore {
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    @Override
    public List<Message> getMessages(String sessionId) {
        return new ArrayList<>(session(sessionId).messages);
    }

    @Override
    public String appendMessage(String sessionId, Message message) {
        message.setId(UUID.randomUUID().toString().replaceAll("-", ""));
        SessionState session = session(sessionId);
        session.messages.add(message);
        return message.getId();
    }

    @Override
    public void addToolExecutions(final String sessionId, final String messageId, final List<ToolExecution> toolExecutions) {
        final SessionState session = session(sessionId);
        toolExecutions.forEach(toolExecution -> {
            toolExecution.setId(UUID.randomUUID().toString().replaceAll("-", ""));
        });
        session.toolExecutions.computeIfAbsent(messageId, _ -> new ArrayList<>()).addAll(toolExecutions);
    }

    @Override
    public Map<String, List<ToolExecution>> getToolExecutions(final String sessionId, final List<String> messageIds) {
        final SessionState session = session(sessionId);
        final Map<String, List<ToolExecution>> toolExecutions = new HashMap<>();
        for (final String messageId : messageIds) {
            toolExecutions.put(messageId, CollectionUtils.nullSafeList(session.toolExecutions.get(messageId)));
        }
        return toolExecutions;
    }

    @Override
    public List<Summary> getSummaries(String sessionId) {
        SessionState session = session(sessionId);
        return new ArrayList<>(session.summaries);
    }

    @Override
    public void addSummary(final String sessionId, final String summarizedFromMessageId, final String summarizedUptoMessageId, final String summary, final long createdAt) {
        SessionState session = session(sessionId);
        session.summaries.add(new Summary(UUID.randomUUID().toString().replaceAll("-", ""), summary, summarizedFromMessageId, summarizedUptoMessageId, createdAt));
    }

    private SessionState session(String sessionId) {
        return sessions.computeIfAbsent(sessionId, _ -> new SessionState());
    }

    private static final class SessionState {
        private final List<Message> messages = new CopyOnWriteArrayList<>();
        private final ConcurrentMap<String, List<ToolExecution>> toolExecutions = new ConcurrentHashMap<>();
        private final List<Summary> summaries = new CopyOnWriteArrayList<>();
    }
}
