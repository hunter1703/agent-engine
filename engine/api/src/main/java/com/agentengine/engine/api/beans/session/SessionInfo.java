package com.agentengine.engine.api.beans.session;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.beans.Secure;
import com.agentengine.engine.api.utils.JsonUtils;
import com.google.adk.events.Event;
import org.bson.codecs.pojo.annotations.BsonIgnore;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@BsonDiscriminator(value = "session_info")
public class SessionInfo extends BaseEntity {
  private static final Logger LOG = LoggerFactory.getLogger(SessionInfo.class);

  private String appName;
  private String userId;
  private Map<String, Object> state = new HashMap<>();
  @BsonIgnore
  private List<Map<String, Object>> events = new ArrayList<>();
  private Long lastUpdateTime;

  public SessionInfo() {}

  public static SessionInfo fromSession(final Session session) {
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setId(session.id());
    sessionInfo.setAppName(session.appName());
    sessionInfo.setUserId(session.userId());
    sessionInfo.setState(
        session.state() == null ? new HashMap<>() : new HashMap<>(session.state()));

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
    final List<Event> sessionEvents = new ArrayList<>();
    if (events != null) {
      for (final Map<String, Object> map : events) {
        if (map == null) {
          continue;
        }
        try {
          sessionEvents.add(JsonUtils.fromJacksonMap(map, Event.class));
        } catch (IllegalArgumentException ex) {
          LOG.debug("Skipping invalid event payload", ex);
        }
      }
    }
    final Session.Builder builder =
        Session.builder(getId())
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

  @BsonIgnore
  public List<Map<String, Object>> getEvents() {
    return events;
  }

  @BsonIgnore
  public void setEvents(final List<Map<String, Object>> events) {
      this.events = events == null ? new ArrayList<>() : events;
  }
  
  @Secure
  public String getEventsJson() {
    return JsonUtils.toJson(events == null ? List.of() : events);
  }

  @SuppressWarnings("unchecked")
  public void setEventsJson(final String json) {
    if (json == null || json.isEmpty()) {
      this.events = new ArrayList<>();
    } else {
      try {
        this.events = (List<Map<String, Object>>) JsonUtils.fromJson(json, List.class);
      } catch (RuntimeException ex) {
        LOG.debug("Failed to deserialize eventsJson payload; defaulting to empty events.", ex);
        this.events = new ArrayList<>();
      }
    }
  }

  public Long getLastUpdateTime() {
    return lastUpdateTime;
  }

  public void setLastUpdateTime(final Long lastUpdateTime) {
    this.lastUpdateTime = lastUpdateTime;
  }
}
