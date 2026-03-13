package com.agentengine.engine.api.beans.session;

import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.Secure;
import com.agentengine.util.common.beans.BaseEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@BsonDiscriminator(value = "session_info")
public class SessionInfo extends BaseEntity {
  private String appName;
  private String userId;
  private Map<String, Object> state = new HashMap<>();
  @BsonIgnore
  private List<Map<String, Object>> events = new ArrayList<>();
  private Long lastUpdateTime;

  public SessionInfo() {
  }

  public static SessionInfo fromSession(final Session session) {
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setId(session.id());
    sessionInfo.setAppName(session.appName());
    sessionInfo.setUserId(session.userId());
    sessionInfo.setState(session.state() == null ? new HashMap<>() : new HashMap<>(session.state()));

    if (session.events() != null) {
      final List<Map<String, Object>> eventMaps = new ArrayList<>();
      for (final Event event : session.events()) {
        eventMaps.add(JsonUtils.toJacksonMap(event));
      }
      sessionInfo.setEvents(eventMaps);
    } else {
      sessionInfo.setEvents(new ArrayList<>());
    }

    sessionInfo.setLastUpdateTime(session.lastUpdateTime().toEpochMilli());
    return sessionInfo;
  }

  public Session toSession() {
    final ConcurrentMap<String, Object> sessionState = new ConcurrentHashMap<>();
    if (state != null) {
      sessionState.putAll(state);
    }
    final Session.Builder builder = Session.builder(getId()).appName(appName).userId(userId).state(sessionState)
        .events(JsonUtils.fromJson(getEventsJson(), new TypeReference<>() {
        }));
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

  @BsonIgnore
  public List<Event> getEvents() {
    final List<Event> parsed = JsonUtils.fromJson(getEventsJson(), new TypeReference<>() {
    });
    return parsed == null ? List.of() : parsed;
  }

  @BsonIgnore
  public void setEvents(final List<Map<String, Object>> events) {
    this.events = events == null ? new ArrayList<>() : events;
  }

  @Secure
  public String getEventsJson() {
    return JsonUtils.toJson(events == null ? List.of() : events);
  }

  @Secure
  public void setEventsJson(final String json) {
    if (json == null || json.isBlank()) {
      setEvents(new ArrayList<>());
      return;
    }
    final List<Map<String, Object>> parsedEvents = JsonUtils.fromJson(json, new TypeReference<>() {
    });
    setEvents(parsedEvents == null ? new ArrayList<>() : parsedEvents);
  }

  public Long getLastUpdateTime() {
    return lastUpdateTime;
  }

  public void setLastUpdateTime(final Long lastUpdateTime) {
    this.lastUpdateTime = lastUpdateTime;
  }
}
