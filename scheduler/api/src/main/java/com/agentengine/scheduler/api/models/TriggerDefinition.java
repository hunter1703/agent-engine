package com.agentengine.scheduler.api.models;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;
import java.util.Map;

@Index(name = "trigger_due_idx", def = "{'status': 1, 'dueAt': 1}")
@Index(name = "trigger_job_idx", def = "{'jobDefinition._id': 1, 'jobDefinition.version': 1}")
@Index(name = "trigger_scheduled_by_idx", def = "{'status': 1, 'scheduledBy': 1}")
public class TriggerDefinition extends BaseEntity {
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_DUE_AT = "dueAt";
    public static final String FIELD_SCHEDULED_FOR = "scheduledFor";
    public static final String FIELD_PREVIOUS_RESULT = "previousResult";
    public static final String FIELD_SCHEDULED_BY = "scheduledBy";
    public static final String FIELD_LAST_HEARTBEAT = "lastHeartbeat";
    public static final String FIELD_JOB_DEFINITION = "jobDefinition";
    public static final String FIELD_JOB_ID = "jobDefinition._id";
    public static final String FIELD_JOB_VERSION = "jobDefinition.version";
    public static final String FIELD_JOB_PAYLOAD = "jobDefinition.payload";

    private JobDefinition jobDefinition;
    private TriggerStatus status = TriggerStatus.WAITING;
    private Map<String, Object> previousResult;
    private String scheduledBy;
    private long lastHeartbeat;
    private long dueAt;
    private long scheduledFor;

    public JobDefinition getJobDefinition() {
        return jobDefinition;
    }

    public void setJobDefinition(final JobDefinition jobDefinition) {
        this.jobDefinition = jobDefinition;
    }

    public TriggerStatus getStatus() {
        return status;
    }

    public void setStatus(final TriggerStatus status) {
        this.status = status;
    }

    public Map<String, Object> getPreviousResult() {
        return previousResult;
    }

    public void setPreviousResult(final Map<String, Object> previousResult) {
        this.previousResult = previousResult;
    }

    /**
     * The scheduler node that moved this trigger to {@link TriggerStatus#QUEUED}. Queueing is a batch
     * update, which reports how many documents it changed but not which, so the node reads back the
     * rows carrying its own id to learn what it actually won. Should a partition ever leave two
     * schedulers running, each dispatches only its own subset rather than both dispatching all.
     */
    public String getScheduledBy() {
        return scheduledBy;
    }

    public void setScheduledBy(final String scheduledBy) {
        this.scheduledBy = scheduledBy;
    }

    /** When the trigger becomes due: {@link #getScheduledFor()} plus jitter. */
    public long getDueAt() {
        return dueAt;
    }

    public void setDueAt(final long dueAt) {
        this.dueAt = dueAt;
    }

    /**
     * The exact cron occurrence this run belongs to, unaffected by jitter. Anchoring the next
     * computation here rather than on {@link #getDueAt()} is what stops a run that jitter moved
     * early from resolving to the same occurrence again. It is also the stable idempotency key a job
     * sees as {@code scheduledTime}.
     */
    public long getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(final long scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(final long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }
}
