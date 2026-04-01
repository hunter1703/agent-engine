package com.agentengine.runtime.session.state;

import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.pekko.PekkoSerializable;
import com.google.adk.events.Event;

import java.util.*;

/** Durable actor state reconstructed from journal facts. */
public record SessionActorState(
        SessionState sessionState,
        Queue<UniqueRecord<String>> queue,
        Map<String, ChildSession> childRegistry,
        Set<StartingChild> startingChildren,
        long nextSequence,
        SessionTopology topology,
        RunResult lastResult,
        PauseState pauseState,
        UniqueRecord<String> currentMessage, String lastPersistedEventId)
        implements PekkoSerializable {

    public static SessionActorState initial() {
        return new SessionActorState(
                SessionState.IDLE, new LinkedList<>(), new HashMap<>(), new HashSet<>(), 0L, null, null, new PauseState(), null, null);
    }

    public SessionActorState withSessionState(final SessionState sessionState) {
        return new SessionActorState(
                sessionState, queue, childRegistry, startingChildren, nextSequence, topology, lastResult, pauseState, currentMessage, lastPersistedEventId);
    }

    public SessionActorState withTopology(final SessionTopology updatedTopology) {
        return new SessionActorState(
                sessionState, queue, childRegistry, startingChildren, nextSequence, updatedTopology, lastResult, pauseState, currentMessage, lastPersistedEventId);
    }

    public SessionActorState withRunResult(final RunResult result) {
        return new SessionActorState(
                sessionState, queue, childRegistry, startingChildren, nextSequence, topology, result, pauseState, currentMessage, lastPersistedEventId);
    }

    public SessionActorState withCurrentMessage(final UniqueRecord<String> updatedCurrentMessage) {
        return new SessionActorState(
                sessionState, queue, childRegistry, startingChildren, nextSequence, topology, lastResult, pauseState, updatedCurrentMessage, lastPersistedEventId);
    }

    public SessionActorState clearCurrentMessage() {
        return new SessionActorState(sessionState, queue, childRegistry, startingChildren, nextSequence, topology, lastResult, pauseState, null, lastPersistedEventId);
    }

    public SessionActorState enqueue(final UniqueRecord<String> message) {
        queue.add(message);
        return this;
    }

    public SessionActorState dequeue() {
        queue.poll();
        return this;
    }

    public SessionActorState withCommitedEvents(final List<Event> events) {
        final String newLastEventId = events.isEmpty() ? lastPersistedEventId : events.getLast().id();
        return new SessionActorState(sessionState, queue, childRegistry, startingChildren,
                nextSequence() + events.size(), topology, lastResult, pauseState, null, newLastEventId);
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

    public SessionActorState childPaused(final String childSessionId, final String confirmationId) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withChildPaused(childSessionId, confirmationId),
                currentMessage, lastPersistedEventId);
    }

    public SessionActorState selfPaused(final String confirmationId) {
        return new SessionActorState(
                SessionState.PAUSED,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withSelfPaused(confirmationId),
                currentMessage, lastPersistedEventId);
    }

    public String getPausedChild(final Confirmation confirmation) {
        return pauseState.getPausedChild(confirmation.getConfirmationId());
    }

    public boolean isSelfConfirmation(final Confirmation confirmation) {
        final String id = confirmation.getConfirmationId();
        return pauseState.getPendingSelfConfirmationIds().contains(id) || pauseState.getReceivedSelfConfirmations().containsKey(id);
    }

    public boolean allConfirmationsReceived() {
        return CollectionUtils.isEmpty(pauseState.getPendingSelfConfirmationIds());
    }

    public Collection<Confirmation> getAllReceivedConfirmations() {
        return pauseState.getReceivedSelfConfirmations().values();
    }

    public SessionActorState selfResume(final Confirmation confirmation) {
        return new SessionActorState(
                SessionState.TRIGGERED_RUN,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withSelfResumed(confirmation),
                currentMessage, lastPersistedEventId);
    }

    public SessionActorState childResume(final Confirmation confirmation) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withChildResumed(confirmation.getConfirmationId()),
                currentMessage, lastPersistedEventId);
    }

    public boolean isDuplicateTurn(final List<Event> turnEvents) {
        return Objects.equals(lastPersistedEventId, turnEvents.getLast().id());
    }
}
