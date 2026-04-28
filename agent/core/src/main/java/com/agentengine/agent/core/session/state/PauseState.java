package com.agentengine.agent.core.session.state;

import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.common.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record PauseState(
        Set<String> pendingExternalSelfConfirmationIds,
        Map<String, Confirmation> receivedSelfConfirmations,
        Map<String, String> pendingConfirmationIdVsChildSessionId,
        Map<String, String> correlationIdVsPendingInternalConfirmationId) {
    public PauseState() {
        this(new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @JsonCreator
    public PauseState(
            @JsonProperty("pendingExternalSelfConfirmationIds") final Set<String> pendingExternalSelfConfirmationIds,
            @JsonProperty("receivedSelfConfirmations") final Map<String, Confirmation> receivedSelfConfirmations,
            @JsonProperty("pendingConfirmationIdVsChildSessionId")
                    final Map<String, String> pendingConfirmationIdVsChildSessionId,
            @JsonProperty("correlationIdVsPendingInternalConfirmationId")
                    final Map<String, String> correlationIdVsPendingInternalConfirmationId) {
        this.pendingExternalSelfConfirmationIds = pendingExternalSelfConfirmationIds == null
                ? new HashSet<>()
                : new HashSet<>(pendingExternalSelfConfirmationIds);
        this.receivedSelfConfirmations = CollectionUtils.nullSafeMutableMap(receivedSelfConfirmations);
        this.pendingConfirmationIdVsChildSessionId =
                CollectionUtils.nullSafeMutableMap(pendingConfirmationIdVsChildSessionId);
        this.correlationIdVsPendingInternalConfirmationId =
                CollectionUtils.nullSafeMutableMap(correlationIdVsPendingInternalConfirmationId);
    }

    public PauseState withChildPaused(final String childSessionId, final String confirmationId) {
        final Map<String, String> updated = new HashMap<>(pendingConfirmationIdVsChildSessionId);
        updated.put(confirmationId, childSessionId);
        return new PauseState(
                pendingExternalSelfConfirmationIds,
                receivedSelfConfirmations,
                updated,
                correlationIdVsPendingInternalConfirmationId);
    }

    public PauseState withSelfPaused(final String confirmationId) {
        final Set<String> updated = new HashSet<>(pendingExternalSelfConfirmationIds);
        updated.add(confirmationId);
        return new PauseState(
                updated,
                receivedSelfConfirmations,
                pendingConfirmationIdVsChildSessionId,
                correlationIdVsPendingInternalConfirmationId);
    }

    public PauseState withSelfConfirmed(final Confirmation confirmation) {
        final String id = confirmation.getConfirmationId();
        final Map<String, Confirmation> updatedReceived = new HashMap<>(receivedSelfConfirmations);
        updatedReceived.put(id, confirmation);
        if (pendingExternalSelfConfirmationIds.contains(id) || receivedSelfConfirmations.containsKey(id)) {
            final Set<String> updatedPending = new HashSet<>(pendingExternalSelfConfirmationIds);
            updatedPending.remove(id);
            return new PauseState(
                    updatedPending,
                    updatedReceived,
                    pendingConfirmationIdVsChildSessionId,
                    correlationIdVsPendingInternalConfirmationId);
        } else {
            final Map<String, String> updatedPending = new HashMap<>(correlationIdVsPendingInternalConfirmationId);
            correlationIdVsPendingInternalConfirmationId.entrySet().stream()
                    .filter(entry -> Objects.equals(entry.getValue(), id))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(updatedPending::remove);
            return new PauseState(
                    pendingExternalSelfConfirmationIds,
                    updatedReceived,
                    pendingConfirmationIdVsChildSessionId,
                    updatedPending);
        }
    }

    public PauseState withChildConfirmed(final String confirmationId) {
        final Map<String, String> updated = new HashMap<>(pendingConfirmationIdVsChildSessionId);
        updated.remove(confirmationId);
        return new PauseState(
                pendingExternalSelfConfirmationIds,
                receivedSelfConfirmations,
                updated,
                correlationIdVsPendingInternalConfirmationId);
    }

    public PauseState withInternalSelfPause(String correlationId, final String confirmationId) {
        final Map<String, String> updated = new HashMap<>(correlationIdVsPendingInternalConfirmationId);
        updated.put(correlationId, confirmationId);
        return new PauseState(
                pendingExternalSelfConfirmationIds,
                receivedSelfConfirmations,
                pendingConfirmationIdVsChildSessionId,
                updated);
    }

    public String getPausedChild(final String confirmationId) {
        return pendingConfirmationIdVsChildSessionId.get(confirmationId);
    }
}
