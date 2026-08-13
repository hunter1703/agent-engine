package com.agentengine.agent.core.session.state;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.agents.beans.ResumeRequest;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.PekkoSerializable;
import com.google.adk.events.Event;
import java.util.*;

/** Durable actor state reconstructed from journal facts. */
public record SessionActorState(
        SessionState sessionState,
        Queue<UniqueRecord<UserMessage>> queue,
        Map<String, ChildSession> childRegistry,
        Set<StartingChild> startingChildren,
        long nextSequence,
        SessionTopology topology,
        RunResult lastResult,
        PauseState pauseState,
        RunState runState)
        implements PekkoSerializable {

    public static SessionActorState initial() {
        return new SessionActorState(
                SessionState.IDLE,
                new LinkedList<>(),
                new HashMap<>(),
                new HashSet<>(),
                0L,
                null,
                null,
                new PauseState(),
                new RunState(null, null));
    }

    public SessionActorState withSessionState(final SessionState sessionState) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState,
                runState);
    }

    public SessionActorState withTopology(final SessionTopology updatedTopology) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                updatedTopology,
                lastResult,
                pauseState,
                runState);
    }

    public SessionActorState withRunResult(final RunResult result) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                result,
                pauseState,
                runState);
    }

    public SessionActorState withCurrentMessage(final UniqueRecord<UserMessage> updatedCurrentMessage) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState,
                runState.withMessage(updatedCurrentMessage));
    }

    public SessionActorState completeRun(RunResult result) {
        return withRunResult(result).withSessionState(SessionState.IDLE).withCurrentMessage(null);
    }

    public SessionActorState enqueue(final UniqueRecord<UserMessage> message) {
        queue.add(message);
        return this;
    }

    public SessionActorState dequeue() {
        queue.poll();
        return this;
    }

    public SessionActorState withCommitedEvents(final List<Event> events) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence() + events.size(),
                topology,
                lastResult,
                pauseState,
                runState.withEvents(events));
    }

    public Optional<ChildSession> child(final String childSessionId) {
        return Optional.ofNullable(childRegistry.get(childSessionId));
    }

    public SessionActorState startingChild(final StartingChild child) {
        startingChildren.add(child);
        return this;
    }

    public SessionActorState startedChild(final String childSessionId, final ChildSession worker) {
        childRegistry.put(childSessionId, worker);
        startingChildren.removeIf(child -> Objects.equals(child.sessionId(), childSessionId));
        return this;
    }

    public SessionActorState childStartFailed(final String sessionId) {
        startingChildren.removeIf(child -> Objects.equals(child.sessionId(), sessionId));
        return this;
    }

    public SessionActorState childPaused(final String childSessionId, final String interruptId) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withChildPaused(childSessionId, interruptId),
                runState);
    }

    public SessionActorState selfPaused(final String interruptId) {
        return new SessionActorState(
                SessionState.PAUSED,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withSelfPaused(interruptId),
                runState);
    }

    public String getPausedChild(final ResumeRequest resumeRequest) {
        return pauseState.getPausedChild(resumeRequest.getInterruptId());
    }

    public boolean isSelfInterrupt(final ResumeRequest resumeRequest) {
        return isExternalSelfInterrupt(resumeRequest)
                || pauseState.correlationIdVsPendingInternalInterruptId().containsValue(resumeRequest.getInterruptId());
    }

    public boolean isExternalSelfInterrupt(final ResumeRequest resumeRequest) {
        final String id = resumeRequest.getInterruptId();
        return pauseState.pendingExternalSelfInterruptIds().contains(id)
                || pauseState.receivedSelfResumes().containsKey(id);
    }

    public boolean allInterruptsAnswered() {
        return CollectionUtils.isEmpty(pauseState.pendingExternalSelfInterruptIds())
                && CollectionUtils.isEmpty(pauseState.correlationIdVsPendingInternalInterruptId());
    }

    public Collection<ResumeRequest> getAllReceivedResumes() {
        return pauseState.receivedSelfResumes().values();
    }

    public SessionActorState withInternalSelfPause(final String correlationId, final String interruptId) {
        return new SessionActorState(
                SessionState.PAUSED,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withInternalSelfPause(correlationId, interruptId),
                runState);
    }

    public boolean isPausedOnExternalInterrupts() {
        return sessionState == SessionState.PAUSED
                && CollectionUtils.isNotEmpty(pauseState.pendingExternalSelfInterruptIds())
                && CollectionUtils.isNotEmpty(pauseState.pendingInterruptIdVsChildSessionId());
    }

    public String getInternalInterruptId(final String correlationId) {
        return pauseState().correlationIdVsPendingInternalInterruptId().get(correlationId);
    }

    public SessionActorState selfResume(final ResumeRequest resumeRequest) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withSelfResumed(resumeRequest),
                runState);
    }

    public SessionActorState childResume(final ResumeRequest resumeRequest) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withChildResumed(resumeRequest.getInterruptId()),
                runState);
    }

    public boolean isDuplicateTurn(final List<Event> turnEvents) {
        if (runState == null) {
            return false;
        }
        final Event last = CollectionUtils.getLast(runState.lastCommittedTurn());
        return Objects.equals(
                last == null ? null : last.id(), turnEvents.getLast().id());
    }

    public SessionActorState clearSelfInterruptStates() {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                new PauseState(
                        new HashSet<>(),
                        new HashMap<>(),
                        pauseState.pendingInterruptIdVsChildSessionId(),
                        new HashMap<>()),
                runState);
    }

    /**
     * Returns a new state with fresh copies of all mutable collections.
     *
     * <p>The per-event methods ({@code enqueue}, {@code dequeue}, {@code startingChild},
     * {@code startedChild}) mutate their backing collections in place and return {@code this} to
     * keep event-replay O(1). As a consequence, states produced by copy-style factory methods
     * ({@code withSessionState}, etc.) share the same {@link LinkedList}/{@link HashMap}/
     * {@link HashSet} instances until the next in-place mutation. This is safe during normal
     * event sourcing because Pekko discards old state references after each event handler
     * returns. At snapshot boundaries, however, the state leaves the actor's private domain, so
     * fresh copies are taken here to ensure the snapshot is fully isolated from any subsequent
     * mutations.
     */
    public SessionActorState copy() {
        return new SessionActorState(
                sessionState,
                new LinkedList<>(queue),
                new HashMap<>(childRegistry),
                new HashSet<>(startingChildren),
                nextSequence,
                topology,
                lastResult,
                pauseState,
                runState);
    }
}
