package com.agentengine.runtime.session.state;

import com.agentengine.util.common.CollectionUtils;
import java.util.Map;
import java.util.Set;

public final class PauseState {
    private Set<String> selfConfirmationIds;
    private Map<String, String> confirmationIdVsChildSessionId;

    public PauseState() {
        this(Set.of(), Map.of());
    }

    public PauseState(final Set<String> selfConfirmationIds, final Map<String, String> confirmationIdVsChildSessionId) {
        this.selfConfirmationIds = CollectionUtils.nullSafeMutableSet(selfConfirmationIds);
        this.confirmationIdVsChildSessionId = CollectionUtils.nullSafeMutableMap(confirmationIdVsChildSessionId);
    }

    public PauseState withChildPaused(final String childSessionId, final String confirmationId) {
        confirmationIdVsChildSessionId.put(confirmationId, childSessionId);
        return this;
    }

    public PauseState withSelfPaused(final String confirmationId) {
        selfConfirmationIds.add(confirmationId);
        return this;
    }

    public PauseState withSelfResumed(final String confirmationId) {
        selfConfirmationIds.remove(confirmationId);
        return this;
    }

    public PauseState withChildResumed(final String confirmationId) {
        confirmationIdVsChildSessionId.remove(confirmationId);
        return this;
    }

    public boolean isSelfConfirmationId(final String confirmationId) {
        return selfConfirmationIds.contains(confirmationId);
    }

    public String getPausedChild(final String confirmationId) {
        return confirmationIdVsChildSessionId.get(confirmationId);
    }

    public Map<String, String> getConfirmationIdVsChildSessionId() {
        return confirmationIdVsChildSessionId;
    }

    public void setConfirmationIdVsChildSessionId(final Map<String, String> confirmationIdVsChildSessionId) {
        this.confirmationIdVsChildSessionId = CollectionUtils.nullSafeMutableMap(confirmationIdVsChildSessionId);
    }

    public Set<String> getSelfConfirmationIds() {
        return selfConfirmationIds;
    }

    public void setSelfConfirmationIds(final Set<String> selfConfirmationIds) {
        this.selfConfirmationIds = CollectionUtils.nullSafeMutableSet(selfConfirmationIds);
    }
}
