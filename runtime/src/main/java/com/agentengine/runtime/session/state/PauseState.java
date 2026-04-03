package com.agentengine.runtime.session.state;

import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PauseState {
    /**
     * Maps each pending confirmation ID (original tool call ID, known at pause time) to its ADK
     * wrapper call ID ({@code adk_request_confirmation} ID, discovered when the confirmation event
     * arrives). The wrapper value is {@code null} until the wrapper event is observed.
     */
    private final Map<String, String> pendingSelfConfirmationIds;

    private final Map<String, Confirmation> receivedSelfConfirmations;
    private final Map<String, String> pendingConfirmationIdVsChildSessionId;

    public PauseState() {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @JsonCreator
    public PauseState(
            @JsonProperty("pendingSelfConfirmationIds") final Map<String, String> pendingSelfConfirmationIds,
            @JsonProperty("receivedSelfConfirmations") final Map<String, Confirmation> receivedSelfConfirmations,
            @JsonProperty("pendingConfirmationIdVsChildSessionId")
                    final Map<String, String> pendingConfirmationIdVsChildSessionId) {
        this.pendingSelfConfirmationIds = CollectionUtils.nullSafeMutableMap(pendingSelfConfirmationIds);
        this.receivedSelfConfirmations = CollectionUtils.nullSafeMutableMap(receivedSelfConfirmations);
        this.pendingConfirmationIdVsChildSessionId =
                CollectionUtils.nullSafeMutableMap(pendingConfirmationIdVsChildSessionId);
    }

    public PauseState withChildPaused(final String childSessionId, final String confirmationId) {
        final Map<String, String> updated = new HashMap<>(pendingConfirmationIdVsChildSessionId);
        updated.put(confirmationId, childSessionId);
        return new PauseState(pendingSelfConfirmationIds, receivedSelfConfirmations, updated);
    }

    public PauseState withSelfPaused(final String confirmationId, final String wrapperCallId) {
        final Map<String, String> updated = new HashMap<>(pendingSelfConfirmationIds);
        updated.put(confirmationId, wrapperCallId);
        return new PauseState(updated, receivedSelfConfirmations, pendingConfirmationIdVsChildSessionId);
    }

    public PauseState withSelfResumed(final Confirmation confirmation) {
        final String id = confirmation.getConfirmationId();
        // Translate to the wrapper call ID so the runner can resume the ADK invocation correctly.
        final String wrapperId = Objects.requireNonNullElse(pendingSelfConfirmationIds.get(id), id);
        final Map<String, Confirmation> updatedReceived = new HashMap<>(receivedSelfConfirmations);
        updatedReceived.put(id, new Confirmation(wrapperId, confirmation.getConfirmed(), confirmation.getAnswer()));
        final Map<String, String> updatedPending = new HashMap<>(pendingSelfConfirmationIds);
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

    public Map<String, String> getPendingConfirmationIdVsChildSessionId() {
        return pendingConfirmationIdVsChildSessionId;
    }

    /** Returns the set of confirmation IDs that are still awaiting a user response. */
    public Set<String> getPendingSelfConfirmationIds() {
        return pendingSelfConfirmationIds.keySet();
    }

    public Map<String, Confirmation> getReceivedSelfConfirmations() {
        return receivedSelfConfirmations;
    }
}
