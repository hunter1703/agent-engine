package com.agentengine.agent.core.session.state;

import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.CollectionUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record PauseState(
    Map<String, TurnRef> pendingExternalSelfInterrupts,
    Map<String, ResumeRequest> receivedSelfResumes,
    Map<String, String> pendingInterruptIdVsChildSessionId,
    Map<String, InternalInterrupt> childSessionIdVsPendingInternalInterrupt) {

  public PauseState() {
    this(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
  }

  @JsonCreator
  public PauseState(
      @JsonProperty("pendingExternalSelfInterrupts")
          final Map<String, TurnRef> pendingExternalSelfInterrupts,
      @JsonProperty("receivedSelfResumes") final Map<String, ResumeRequest> receivedSelfResumes,
      @JsonProperty("pendingInterruptIdVsChildSessionId")
          final Map<String, String> pendingInterruptIdVsChildSessionId,
      @JsonProperty("childSessionIdVsPendingInternalInterrupt")
          final Map<String, InternalInterrupt> childSessionIdVsPendingInternalInterrupt) {
    this.pendingExternalSelfInterrupts =
        CollectionUtils.nullSafeMutableMap(pendingExternalSelfInterrupts);
    this.receivedSelfResumes = CollectionUtils.nullSafeMutableMap(receivedSelfResumes);
    this.pendingInterruptIdVsChildSessionId =
        CollectionUtils.nullSafeMutableMap(pendingInterruptIdVsChildSessionId);
    this.childSessionIdVsPendingInternalInterrupt =
        CollectionUtils.nullSafeMutableMap(childSessionIdVsPendingInternalInterrupt);
  }

  public PauseState withChildPaused(final String childSessionId, final String interruptId) {
    final Map<String, String> updated = new HashMap<>(pendingInterruptIdVsChildSessionId);
    updated.put(interruptId, childSessionId);
    return new PauseState(
        pendingExternalSelfInterrupts,
        receivedSelfResumes,
        updated,
        childSessionIdVsPendingInternalInterrupt);
  }

  public PauseState withSelfPaused(final String interruptId, final String runId, final int turnId) {
    final Map<String, TurnRef> updated = new HashMap<>(pendingExternalSelfInterrupts);
    updated.put(interruptId, new TurnRef(runId, turnId));
    return new PauseState(
        updated,
        receivedSelfResumes,
        pendingInterruptIdVsChildSessionId,
        childSessionIdVsPendingInternalInterrupt);
  }

  public PauseState withSelfResumed(final ResumeRequest resumeRequest) {
    final String id = resumeRequest.getInterruptId();
    final Map<String, ResumeRequest> updatedReceived = new HashMap<>(receivedSelfResumes);
    updatedReceived.put(id, resumeRequest);
    if (pendingExternalSelfInterrupts.containsKey(id) || receivedSelfResumes.containsKey(id)) {
      final Map<String, TurnRef> updatedPending = new HashMap<>(pendingExternalSelfInterrupts);
      updatedPending.remove(id);
      return new PauseState(
          updatedPending,
          updatedReceived,
          pendingInterruptIdVsChildSessionId,
          childSessionIdVsPendingInternalInterrupt);
    }
    final Map<String, InternalInterrupt> updatedPending =
        new HashMap<>(childSessionIdVsPendingInternalInterrupt);
    childSessionIdVsPendingInternalInterrupt.entrySet().stream()
        .filter(entry -> Objects.equals(entry.getValue().interruptId(), id))
        .map(Map.Entry::getKey)
        .findFirst()
        .ifPresent(updatedPending::remove);
    return new PauseState(
        pendingExternalSelfInterrupts,
        updatedReceived,
        pendingInterruptIdVsChildSessionId,
        updatedPending);
  }

  public PauseState withChildResumed(final String interruptId) {
    final Map<String, String> updated = new HashMap<>(pendingInterruptIdVsChildSessionId);
    updated.remove(interruptId);
    return new PauseState(
        pendingExternalSelfInterrupts,
        receivedSelfResumes,
        updated,
        childSessionIdVsPendingInternalInterrupt);
  }

  public PauseState withInternalSelfPause(
      final String childSessionId, final String interruptId, final String runId, final int turnId) {
    final Map<String, InternalInterrupt> updated =
        new HashMap<>(childSessionIdVsPendingInternalInterrupt);
    updated.put(childSessionId, new InternalInterrupt(interruptId, new TurnRef(runId, turnId)));
    return new PauseState(
        pendingExternalSelfInterrupts,
        receivedSelfResumes,
        pendingInterruptIdVsChildSessionId,
        updated);
  }

  // will not discard if already received resume
  public PauseState withInterruptDiscarded(final String interruptId) {
    final Map<String, TurnRef> updatedExternal = new HashMap<>(pendingExternalSelfInterrupts);
    updatedExternal.remove(interruptId);
    final Map<String, InternalInterrupt> updatedInternal =
        new HashMap<>(childSessionIdVsPendingInternalInterrupt);
    updatedInternal
        .entrySet()
        .removeIf(e -> Objects.equals(e.getValue().interruptId(), interruptId));
    return new PauseState(
        updatedExternal, receivedSelfResumes, pendingInterruptIdVsChildSessionId, updatedInternal);
  }

  public String getPausedChild(final String interruptId) {
    return pendingInterruptIdVsChildSessionId.get(interruptId);
  }

  public record TurnRef(String runId, int turnId) {}

  public record InternalInterrupt(String interruptId, TurnRef turnRef) {}
}
