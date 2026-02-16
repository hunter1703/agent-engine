package com.agentengine.engine.sessions;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.utils.JsonUtils;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@BsonDiscriminator(value = "session_info")
public class SessionInfo extends BaseEntity {
  private static final Logger LOG = LoggerFactory.getLogger(SessionInfo.class);

  private String appName;
  private String userId;
  private Map<String, Object> state = new HashMap<>();
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
    sessionInfo.setEvents(session.events() == null
            ? new ArrayList<>()
            : session.events().stream().map(JsonUtils::toJacksonMap).toList());
    sessionInfo.setLastUpdateTime(session.lastUpdateTime().toEpochMilli());
    return sessionInfo;
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
        try {
          sessionEvents.add(JsonUtils.fromJacksonMap(eventMap, Event.class));
        } catch (IllegalArgumentException ex) {
          LOG.debug("Skipping invalid event payload", ex);
        }
      }
    }
    final Session.Builder builder = Session.builder(getId()).appName(appName).userId(userId).state(sessionState)
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

  public Long getLastUpdateTime() {
    return lastUpdateTime;
  }

  public void setLastUpdateTime(final Long lastUpdateTime) {
    this.lastUpdateTime = lastUpdateTime;
  }
}
