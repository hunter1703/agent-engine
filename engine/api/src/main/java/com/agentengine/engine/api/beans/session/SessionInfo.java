package com.agentengine.engine.api.beans.session;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.Secure;
import com.agentengine.util.common.beans.BaseEntity;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;

public class SessionInfo extends BaseEntity {

  private String appName;
  private String userId;
  private Map<String, Object> state = new HashMap<>();
  @BsonIgnore
  private List<Map<String, Object>> events = new ArrayList<>();
  private Long lastUpdateTime;

  public SessionInfo() {
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
    return CollectionUtils.nullSafeList(JsonUtils.fromJson(getEventsJson(), new TypeReference<>() {
    }));
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
