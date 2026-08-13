package com.agentengine.agent.core.session.state;

import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record PauseState(
        Set<String> pendingExternalSelfInterruptIds,
        Map<String, ResumeRequest> receivedSelfResumes,
        Map<String, String> pendingInterruptIdVsChildSessionId,
        Map<String, String> correlationIdVsPendingInternalInterruptId) {
    public PauseState() {
        this(new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @JsonCreator
    public PauseState(
            @JsonProperty("pendingExternalSelfInterruptIds") final Set<String> pendingExternalSelfInterruptIds,
            @JsonProperty("receivedSelfResumes") final Map<String, ResumeRequest> receivedSelfResumes,
            @JsonProperty("pendingInterruptIdVsChildSessionId")
                    final Map<String, String> pendingInterruptIdVsChildSessionId,
            @JsonProperty("correlationIdVsPendingInternalInterruptId")
                    final Map<String, String> correlationIdVsPendingInternalInterruptId) {
        this.pendingExternalSelfInterruptIds = pendingExternalSelfInterruptIds == null
                ? new HashSet<>()
                : new HashSet<>(pendingExternalSelfInterruptIds);
        this.receivedSelfResumes = CollectionUtils.nullSafeMutableMap(receivedSelfResumes);
        this.pendingInterruptIdVsChildSessionId =
                CollectionUtils.nullSafeMutableMap(pendingInterruptIdVsChildSessionId);
        this.correlationIdVsPendingInternalInterruptId =
                CollectionUtils.nullSafeMutableMap(correlationIdVsPendingInternalInterruptId);
    }

    public PauseState withChildPaused(final String childSessionId, final String interruptId) {
        final Map<String, String> updated = new HashMap<>(pendingInterruptIdVsChildSessionId);
        updated.put(interruptId, childSessionId);
        return new PauseState(
                pendingExternalSelfInterruptIds,
                receivedSelfResumes,
                updated,
                correlationIdVsPendingInternalInterruptId);
    }

    public PauseState withSelfPaused(final String interruptId) {
        final Set<String> updated = new HashSet<>(pendingExternalSelfInterruptIds);
        updated.add(interruptId);
        return new PauseState(
                updated,
                receivedSelfResumes,
                pendingInterruptIdVsChildSessionId,
                correlationIdVsPendingInternalInterruptId);
    }

    public PauseState withSelfResumed(final ResumeRequest resumeRequest) {
        final String id = resumeRequest.getInterruptId();
        final Map<String, ResumeRequest> updatedReceived = new HashMap<>(receivedSelfResumes);
        updatedReceived.put(id, resumeRequest);
        if (pendingExternalSelfInterruptIds.contains(id) || receivedSelfResumes.containsKey(id)) {
            final Set<String> updatedPending = new HashSet<>(pendingExternalSelfInterruptIds);
            updatedPending.remove(id);
            return new PauseState(
                    updatedPending,
                    updatedReceived,
                    pendingInterruptIdVsChildSessionId,
                    correlationIdVsPendingInternalInterruptId);
        } else {
            final Map<String, String> updatedPending = new HashMap<>(correlationIdVsPendingInternalInterruptId);
            correlationIdVsPendingInternalInterruptId.entrySet().stream()
                    .filter(entry -> Objects.equals(entry.getValue(), id))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(updatedPending::remove);
            return new PauseState(
                    pendingExternalSelfInterruptIds,
                    updatedReceived,
                    pendingInterruptIdVsChildSessionId,
                    updatedPending);
        }
    }

    public PauseState withChildResumed(final String interruptId) {
        final Map<String, String> updated = new HashMap<>(pendingInterruptIdVsChildSessionId);
        updated.remove(interruptId);
        return new PauseState(
                pendingExternalSelfInterruptIds,
                receivedSelfResumes,
                updated,
                correlationIdVsPendingInternalInterruptId);
    }

    public PauseState withInternalSelfPause(String correlationId, final String interruptId) {
        final Map<String, String> updated = new HashMap<>(correlationIdVsPendingInternalInterruptId);
        updated.put(correlationId, interruptId);
        return new PauseState(
                pendingExternalSelfInterruptIds, receivedSelfResumes, pendingInterruptIdVsChildSessionId, updated);
    }

    public String getPausedChild(final String interruptId) {
        return pendingInterruptIdVsChildSessionId.get(interruptId);
    }
}
