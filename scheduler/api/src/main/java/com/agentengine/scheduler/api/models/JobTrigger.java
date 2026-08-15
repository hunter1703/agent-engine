package com.agentengine.scheduler.api.models;

import com.agentengine.util.common.beans.BaseEntity;
import java.util.Map;

public class JobTrigger extends BaseEntity {
    private Job job;
    private TriggerStatus status = TriggerStatus.QUEUED;
    private long lastHeartbeat;
    private Map<String, Object> previousResult;
    private long nextTriggerAt;

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public TriggerStatus getStatus() {
        return status;
    }

    public void setStatus(final TriggerStatus status) {
        this.status = status;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(final long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Map<String, Object> getPreviousResult() {
        return previousResult;
    }

    public void setPreviousResult(final java.util.Map<String, Object> previousResult) {
        this.previousResult = previousResult;
    }

    public long getNextTriggerAt() {
        return nextTriggerAt;
    }

    public void setNextTriggerAt(final long nextTriggerAt) {
        this.nextTriggerAt = nextTriggerAt;
    }
}
