package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

import java.util.Set;

/**
 * Sealed execution state machine for a session actor.
 * Every state variant carries the message queue, forcing each transition
 * to explicitly decide what happens to queued messages.
 */
public sealed interface ExecutionState extends PekkoSerializable
        permits ExecutionState.Idle, ExecutionState.Running, ExecutionState.Paused {

    MessageQueue queue();
    ExecutionState withQueue(MessageQueue queue);

    record Idle(MessageQueue queue) implements ExecutionState {
        @Override public ExecutionState withQueue(MessageQueue queue) { return new Idle(queue); }
    }

    record Running(String runId, int retryCount, MessageQueue queue) implements ExecutionState {
        @Override public ExecutionState withQueue(MessageQueue queue) {
            return new Running(runId, retryCount, queue);
        }
    }

    record Paused(
            String runId,
            Set<String> confirmationIds,
            ResumeTarget resumeTarget,
            MessageQueue queue
    ) implements ExecutionState {
        @Override public ExecutionState withQueue(MessageQueue queue) {
            return new Paused(runId, confirmationIds, resumeTarget, queue);
        }
    }
}
