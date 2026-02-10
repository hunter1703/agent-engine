package com.agentengine.interfaces.rest.models;

import com.agentengine.engine.api.beans.config.AgentConfig;
import java.time.Instant;

public class AgentInfo {

    private String id;
    private String object = "agent";
    private long created;
    private String ownedBy;
    private String name;
    private String description;

    public AgentInfo() {
        this.created = Instant.now().getEpochSecond();
    }

    public AgentInfo(String id, String ownedBy) {
        this.id = id;
        this.ownedBy = ownedBy;
        this.created = Instant.now().getEpochSecond();
        this.object = "agent";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getOwnedBy() {
        return ownedBy;
    }

    public void setOwnedBy(String ownedBy) {
        this.ownedBy = ownedBy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }
    
    public static AgentInfo fromAgentConfig(AgentConfig agentConfig) {
        AgentInfo info = new AgentInfo();
        info.setId(agentConfig.getAgentId());
        info.setName(agentConfig.getName() != null ? agentConfig.getName() : agentConfig.getAgentId());
        info.setDescription(agentConfig.getDescription());
        info.setOwnedBy("agent-engine");
        info.setCreated(java.time.Instant.now().getEpochSecond());
        return info;
    }
}
