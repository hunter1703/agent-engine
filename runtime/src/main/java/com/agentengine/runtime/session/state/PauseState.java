package com.agentengine.runtime.session.state;

import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.CollectionUtils;

import java.util.*;

public final class PauseState {
    private Set<String> pendingSelfConfirmationIds;
    private Map<String, Confirmation> receivedSelfConfirmations;
    private Map<String, String> pendingConfirmationIdVsChildSessionId;

    public PauseState() {
        this(new HashSet<>(), new HashMap<>(), new HashMap<>());
    }

    public PauseState(
            final Set<String> pendingSelfConfirmationIds,
            final Map<String, Confirmation> receivedSelfConfirmations,
            final Map<String, String> pendingConfirmationIdVsChildSessionId) {
        this.pendingSelfConfirmationIds = CollectionUtils.nullSafeMutableSet(pendingSelfConfirmationIds);
        this.receivedSelfConfirmations = CollectionUtils.nullSafeMutableMap(receivedSelfConfirmations);
        this.pendingConfirmationIdVsChildSessionId = CollectionUtils.nullSafeMutableMap(pendingConfirmationIdVsChildSessionId);
    }

    public PauseState withChildPaused(final String childSessionId, final String confirmationId) {
        pendingConfirmationIdVsChildSessionId.put(confirmationId, childSessionId);
        return this;
    }

    public PauseState withSelfPaused(final String confirmationId) {
        pendingSelfConfirmationIds.add(confirmationId);
        return this;
    }

    public PauseState withSelfResumed(final Confirmation confirmation) {
        receivedSelfConfirmations.put(confirmation.getConfirmationId(), confirmation);
        pendingSelfConfirmationIds.remove(confirmation.getConfirmationId());
        return this;
    }

    public PauseState withChildResumed(final String confirmationId) {
        pendingConfirmationIdVsChildSessionId.remove(confirmationId);
        return this;
    }

    public String getPausedChild(final String confirmationId) {
        return pendingConfirmationIdVsChildSessionId.get(confirmationId);
    }

    public Map<String, String> getPendingConfirmationIdVsChildSessionId() {
        return pendingConfirmationIdVsChildSessionId;
    }

    public void setPendingConfirmationIdVsChildSessionId(final Map<String, String> pendingConfirmationIdVsChildSessionId) {
        this.pendingConfirmationIdVsChildSessionId = CollectionUtils.nullSafeMutableMap(pendingConfirmationIdVsChildSessionId);
    }

    public Set<String> getPendingSelfConfirmationIds() {
        return pendingSelfConfirmationIds;
    }

    public void setPendingSelfConfirmationIds(final Set<String> pendingSelfConfirmationIds) {
        this.pendingSelfConfirmationIds = CollectionUtils.nullSafeMutableSet(pendingSelfConfirmationIds);
    }

    public Map<String, Confirmation> getReceivedSelfConfirmations() {
        return receivedSelfConfirmations;
    }

    public void setReceivedSelfConfirmations(final Map<String, Confirmation> receivedSelfConfirmations) {
        this.receivedSelfConfirmations = CollectionUtils.nullSafeMutableMap(receivedSelfConfirmations);
    }
}
