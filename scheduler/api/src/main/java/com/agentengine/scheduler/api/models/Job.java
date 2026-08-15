package com.agentengine.scheduler.api.models;

import com.agentengine.util.common.beans.BaseEntity;
import java.util.Map;

public class Job extends BaseEntity {
    private String type;
    private String cronSchedule;
    private Map<String, Object> payload;

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getCronSchedule() {
        return cronSchedule;
    }

    public void setCronSchedule(final String cronSchedule) {
        this.cronSchedule = cronSchedule;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(final Map<String, Object> payload) {
        this.payload = payload;
    }
}
