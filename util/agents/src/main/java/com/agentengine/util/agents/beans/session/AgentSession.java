package com.agentengine.util.agents.beans.session;

import com.agentengine.util.common.Secure;
import com.agentengine.util.common.beans.NamedEntity;
import com.agui.core.event.BaseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentSession extends NamedEntity {
    public static final String DEFAULT_USER_ID = "default";
    public static final String FIELD_STATE = "state";
    public static final String FIELD_SUMMARY = "summary";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_CLOSED_AT = "closedAt";

    private String agentId;
    private Map<String, Object> state = new HashMap<>();
    private List<BaseEvent> aguiEvents;
    private String rootSessionId;
    private String rootAgentId;
    private String parentSessionId;
    private int depth;
    private String spawnedByAgentId;
    private String summary;
    private SessionStatus status;

    public AgentSession() {}

    public AgentSession(final String id, final String agentId, final Map<String, Object> state) {
        setId(id);
        setCreatedTime(System.currentTimeMillis());
        setUpdatedTime(System.currentTimeMillis());
        this.agentId = agentId;
        setName("Untitled Session");
        this.state = state == null ? new HashMap<>() : new HashMap<>(state);
        this.rootSessionId = id;
        this.depth = 0;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(final String agentId) {
        this.agentId = agentId;
    }

    @Secure
    @Override
    public String getName() {
        return super.getName();
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void setState(final Map<String, Object> state) {
        this.state = state == null ? new HashMap<>() : new HashMap<>(state);
    }

    public List<BaseEvent> getAguiEvents() {
        return aguiEvents;
    }

    public void setAguiEvents(final List<BaseEvent> aguiEvents) {
        this.aguiEvents = aguiEvents;
    }

    public String getRootSessionId() {
        return rootSessionId;
    }

    public void setRootSessionId(final String rootSessionId) {
        this.rootSessionId = rootSessionId;
    }

    public String getRootAgentId() {
        return rootAgentId;
    }

    public void setRootAgentId(final String rootAgentId) {
        this.rootAgentId = rootAgentId;
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    public void setParentSessionId(final String parentSessionId) {
        this.parentSessionId = parentSessionId;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(final int depth) {
        this.depth = Math.max(depth, 0);
    }

    public String getSpawnedByAgentId() {
        return spawnedByAgentId;
    }

    public void setSpawnedByAgentId(final String spawnedByAgentId) {
        this.spawnedByAgentId = spawnedByAgentId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(final String summary) {
        this.summary = summary;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(final SessionStatus status) {
        this.status = status;
    }
}
