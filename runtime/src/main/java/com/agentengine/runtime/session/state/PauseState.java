package com.agentengine.runtime.session.state;

import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record PauseState(Set<String> pendingSelfConfirmationIds, Map<String, Confirmation> receivedSelfConfirmations,
                         Map<String, String> pendingConfirmationIdVsChildSessionId) {
    public PauseState() {
        this(new HashSet<>(), new HashMap<>(), new HashMap<>());
    }

    @JsonCreator
    public PauseState(
            @JsonProperty("pendingSelfConfirmationIds") final Set<String> pendingSelfConfirmationIds,
            @JsonProperty("receivedSelfConfirmations") final Map<String, Confirmation> receivedSelfConfirmations,
            @JsonProperty("pendingConfirmationIdVsChildSessionId") final Map<String, String> pendingConfirmationIdVsChildSessionId) {
        this.pendingSelfConfirmationIds =
                pendingSelfConfirmationIds == null ? new HashSet<>() : new HashSet<>(pendingSelfConfirmationIds);
        this.receivedSelfConfirmations = CollectionUtils.nullSafeMutableMap(receivedSelfConfirmations);
        this.pendingConfirmationIdVsChildSessionId =
                CollectionUtils.nullSafeMutableMap(pendingConfirmationIdVsChildSessionId);
    }

    public PauseState withChildPaused(final String childSessionId, final String confirmationId) {
        final Map<String, String> updated = new HashMap<>(pendingConfirmationIdVsChildSessionId);
        updated.put(confirmationId, childSessionId);
        return new PauseState(pendingSelfConfirmationIds, receivedSelfConfirmations, updated);
    }

    public PauseState withSelfPaused(final String confirmationId) {
        final Set<String> updated = new HashSet<>(pendingSelfConfirmationIds);
        updated.add(confirmationId);
        return new PauseState(updated, receivedSelfConfirmations, pendingConfirmationIdVsChildSessionId);
    }

    public PauseState withSelfResumed(final Confirmation confirmation) {
        final String id = confirmation.getConfirmationId();
        final Map<String, Confirmation> updatedReceived = new HashMap<>(receivedSelfConfirmations);
        updatedReceived.put(id, confirmation);
        final Set<String> updatedPending = new HashSet<>(pendingSelfConfirmationIds);
        updatedPending.remove(id);
        return new PauseState(updatedPending, updatedReceived, pendingConfirmationIdVsChildSessionId);
    }

    public PauseState withChildResumed(final String confirmationId) {
        final Map<String, String> updated = new HashMap<>(pendingConfirmationIdVsChildSessionId);
        updated.remove(confirmationId);
        return new PauseState(pendingSelfConfirmationIds, receivedSelfConfirmations, updated);
    }

    public String getPausedChild(final String confirmationId) {
        return pendingConfirmationIdVsChildSessionId.get(confirmationId);
    }
}
