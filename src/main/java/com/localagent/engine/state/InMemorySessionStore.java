package com.localagent.engine.state;

import com.localagent.engine.message.Message;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySessionStore implements SessionStore {
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    @Override
    public String newSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new SessionState());
        return sessionId;
    }

    @Override
    public void resetSession(String sessionId) {
        sessions.put(sessionId, new SessionState());
    }

    @Override
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public List<Message> getMessages(String sessionId) {
        return new ArrayList<>(session(sessionId).messages);
    }

    @Override
    public int appendMessage(String sessionId, Message message) {
        SessionState session = session(sessionId);
        session.messages.add(message);
        return session.messages.size() - 1;
    }

    @Override
    public List<ToolExecution> getToolExecutions(String sessionId, int parentIndex) {
        SessionState session = session(sessionId);
        return new ArrayList<>(session.toolExecutions.getOrDefault(parentIndex, List.of()));
    }

    @Override
    public void recordToolExecution(String sessionId, int parentIndex, ToolExecution record) {
        SessionState session = session(sessionId);
        session.toolExecutions
            .computeIfAbsent(parentIndex, key -> Collections.synchronizedList(new ArrayList<>()))
            .add(record);
    }

    @Override
    public void recordEvent(String sessionId, Map<String, Object> event) {
        SessionState session = session(sessionId);
        Map<String, Object> payload = new HashMap<>(event);
        payload.putIfAbsent("created_at", Instant.now().toString());
        session.events.add(payload);
    }

    @Override
    public List<Map<String, Object>> getEvents(String sessionId) {
        SessionState session = session(sessionId);
        return new ArrayList<>(session.events);
    }

    @Override
    public List<Map<String, Object>> getSummaries(String sessionId) {
        SessionState session = session(sessionId);
        return new ArrayList<>(session.summaries);
    }

    @Override
    public void addSummary(String sessionId, int uptoIndex, String summary, int tokens, String createdAt) {
        SessionState session = session(sessionId);
        Map<String, Object> record = new HashMap<>();
        record.put("upto_idx", uptoIndex);
        record.put("summary", summary);
        record.put("tokens", tokens);
        record.put("created_at", createdAt);
        session.summaries.add(record);
    }

    @Override
    public void dropOldestSummary(String sessionId) {
        SessionState session = session(sessionId);
        if (!session.summaries.isEmpty()) {
            session.summaries.remove(0);
        }
    }

    @Override
    public void dropMessagesUntil(String sessionId, int untilIndex) {
        SessionState session = session(sessionId);
        if (untilIndex <= 0) {
            return;
        }
        if (untilIndex >= session.messages.size()) {
            session.messages.clear();
            session.toolExecutions.clear();
            return;
        }
        session.messages.subList(0, untilIndex).clear();
        session.toolExecutions.entrySet().removeIf(entry -> entry.getKey() < untilIndex);
    }

    @Override
    public void incrementSteps(String sessionId, int amount) {
        SessionState session = session(sessionId);
        session.steps += amount;
    }

    @Override
    public int getSteps(String sessionId) {
        return session(sessionId).steps;
    }

    private SessionState session(String sessionId) {
        return sessions.computeIfAbsent(sessionId, key -> new SessionState());
    }

    private static final class SessionState {
        private final List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        private final Map<Integer, List<ToolExecution>> toolExecutions = new ConcurrentHashMap<>();
        private final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList<>());
        private final List<Map<String, Object>> summaries = Collections.synchronizedList(new ArrayList<>());
        private int steps = 0;
    }
}
