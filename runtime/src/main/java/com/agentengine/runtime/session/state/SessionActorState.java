package com.agentengine.runtime.session.state;

import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.SessionEvent;
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
        UniqueRecord<String> currentMessage,
        List<Event> lastCommittedEvents)
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
                null,
                null);
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
                currentMessage,
                lastCommittedEvents);
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
                currentMessage,
                lastCommittedEvents);
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
                currentMessage,
                lastCommittedEvents);
    }

    public SessionActorState withCurrentMessage(final UniqueRecord<String> updatedCurrentMessage) {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState,
                updatedCurrentMessage,
                lastCommittedEvents);
    }

    public SessionActorState clearCurrentMessage() {
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState,
                null,
                lastCommittedEvents);
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
        return new SessionActorState(
                sessionState,
                queue,
                childRegistry,
                startingChildren,
                nextSequence() + events.size(),
                topology,
                lastResult,
                pauseState,
                null,
                events);
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
                currentMessage,
                lastCommittedEvents);
    }

    public SessionActorState selfPaused(final String confirmationId, final String wrapperCallId) {
        return new SessionActorState(
                SessionState.PAUSED,
                queue,
                childRegistry,
                startingChildren,
                nextSequence,
                topology,
                lastResult,
                pauseState.withSelfPaused(confirmationId, wrapperCallId),
                currentMessage,
                lastCommittedEvents);
    }

    public String getPausedChild(final Confirmation confirmation) {
        return pauseState.getPausedChild(confirmation.getConfirmationId());
    }

    public boolean isSelfConfirmation(final Confirmation confirmation) {
        final String id = confirmation.getConfirmationId();
        return pauseState.getPendingSelfConfirmationIds().contains(id)
                || pauseState.getReceivedSelfConfirmations().containsKey(id);
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
                currentMessage,
                lastCommittedEvents);
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
                currentMessage,
                lastCommittedEvents);
    }

    public boolean isDuplicateTurn(final List<Event> turnEvents) {
        final Event last = CollectionUtils.getLast(lastCommittedEvents);
        return Objects.equals(last == null ? null : last.id(), turnEvents.getLast().id());
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
                currentMessage,
                lastCommittedEvents);
    }
}
