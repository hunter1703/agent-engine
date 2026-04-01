package com.agentengine.runtime.session.state;

import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.util.pekko.PekkoSerializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.UnaryOperator;

/** Durable actor state reconstructed from journal facts. */
public record ActorState(
        SessionState session,
        Queue<String> queue,
        Map<String, ChildSession> childRegistry,
        long nextSequence,
        SessionTopology topology,
        RunResult lastResult,
        PauseState pauseState,
        String currentMessage)
        implements PekkoSerializable {

    public static ActorState initial() {
        return new ActorState(
                SessionState.IDLE, new LinkedList<>(), new HashMap<>(), 0L, null, null, new PauseState(), null);
    }

    public ActorState withSessionState(final SessionState sessionState) {
        return new ActorState(
                sessionState, queue, childRegistry, nextSequence, topology, lastResult, pauseState, currentMessage);
    }

    public ActorState withQueue(final Queue<String> updatedQueue) {
        return new ActorState(
                session, updatedQueue, childRegistry, nextSequence, topology, lastResult, pauseState, currentMessage);
    }

    public ActorState withNextSequence(final long updatedNextSequence) {
        return new ActorState(
                session, queue, childRegistry, nextSequence, topology, lastResult, pauseState, currentMessage);
    }

    public ActorState withTopology(final SessionTopology updatedTopology) {
        return new ActorState(
                session, queue, childRegistry, nextSequence, updatedTopology, lastResult, pauseState, currentMessage);
    }

    public ActorState withRunResult(final RunResult result) {
        return new ActorState(
                session, queue, childRegistry, nextSequence, topology, result, pauseState, currentMessage);
    }

    public ActorState withCurrentMessage(final String updatedCurrentMessage) {
        return new ActorState(
                session, queue, childRegistry, nextSequence, topology, lastResult, pauseState, updatedCurrentMessage);
    }

    public ActorState clearCurrentMessage() {
        return new ActorState(session, queue, childRegistry, nextSequence, topology, lastResult, pauseState, null);
    }

    public ActorState enqueue(final String message) {
        queue.add(message);
        return this;
    }

    public ActorState dequeue() {
        queue.poll();
        return this;
    }

    public Optional<ChildSession> child(final String childSessionId) {
        return Optional.ofNullable(childRegistry.get(childSessionId));
    }

    public ActorState registerChild(final String childSessionId, final ChildSession worker) {
        childRegistry.put(childSessionId, worker);
        return this;
    }

    public ActorState updateChild(final String childSessionId, final UnaryOperator<ChildSession> fn) {
        final ChildSession worker = childRegistry.get(childSessionId);
        if (worker == null) {
            return this;
        }
        childRegistry.put(childSessionId, fn.apply(worker));
        return this;
    }

    public ActorState childPaused(final String childSessionId, final String confirmationId) {
        return new ActorState(
                session,
                queue,
                childRegistry,
                nextSequence,
                topology,
                lastResult,
                pauseState.withChildPaused(childSessionId, confirmationId),
                currentMessage);
    }

    public ActorState selfPaused(final String confirmationId) {
        return new ActorState(
                SessionState.PAUSED,
                queue,
                childRegistry,
                nextSequence,
                topology,
                lastResult,
                pauseState.withSelfPaused(confirmationId),
                currentMessage);
    }

    public String getPausedChild(final String confirmationId) {
        return pauseState.getPausedChild(confirmationId);
    }

    public boolean isSelfConfirmationId(final String confirmationId) {
        return pauseState.isSelfConfirmationId(confirmationId);
    }

    public ActorState selfResume(final String confirmationId) {
        return new ActorState(
                SessionState.RUNNING,
                queue,
                childRegistry,
                nextSequence,
                topology,
                lastResult,
                pauseState.withSelfResumed(confirmationId),
                currentMessage);
    }

    public ActorState childResume(final String confirmationId) {
        return new ActorState(
                session,
                queue,
                childRegistry,
                nextSequence,
                topology,
                lastResult,
                pauseState.withChildResumed(confirmationId),
                currentMessage);
    }
}
