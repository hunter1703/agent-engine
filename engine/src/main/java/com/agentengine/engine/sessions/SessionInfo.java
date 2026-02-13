package com.agentengine.engine.sessions;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.utils.JsonUtils;
import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.TemporalField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bson.codecs.pojo.annotations.BsonId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionInfo extends BaseEntity {
    private String appName;
    private String userId;
    private Map<String, Object> state = new HashMap<>();
    private List<Map<String, Object>> events = new ArrayList<>();
    private Long lastUpdateTime;

    public SessionInfo() {
    }

    public SessionInfo(final Session session) {
        setId(session.id());
        this.appName = session.appName();
        this.userId = session.userId();
        this.state = session.state() == null ? new HashMap<>() : new HashMap<>(session.state());
        this.events = session.events() == null
                ? new ArrayList<>()
                : session.events().stream()
                .map(JsonUtils::toMap)
                .toList();
        this.lastUpdateTime = session.lastUpdateTime().toEpochMilli();
    }

    public Session toSession() {
        final ConcurrentMap<String, Object> sessionState = new ConcurrentHashMap<>();
        if (state != null) {
            sessionState.putAll(state);
        }
        final List<Event> sessionEvents = new ArrayList<>();
        if (events != null) {
            for (final Map<String, Object> eventMap : events) {
                if (eventMap == null) {
                    continue;
                }
                sessionEvents.add(JsonUtils.fromMap(eventMap, Event.class));
            }
        }
        final Session.Builder builder = Session.builder(getId())
                .appName(appName)
                .userId(userId)
                .state(sessionState)
                .events(sessionEvents);
        if (lastUpdateTime != null) {
            builder.lastUpdateTime(Instant.ofEpochMilli(lastUpdateTime));
        }
        return builder.build();
    }

    @Override
    @BsonId
    public String getId() {
        return super.getId();
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(final String appName) {
        this.appName = appName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void setState(final Map<String, Object> state) {
        this.state = state;
    }

    public List<Map<String, Object>> getEvents() {
        return events;
    }

    public void setEvents(final List<Map<String, Object>> events) {
        this.events = events;
    }

    public double getLastUpdateTime() {
        return lastUpdateTime == null ? 0.0 : lastUpdateTime;
    }

    public void setLastUpdateTime(final Long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
}
