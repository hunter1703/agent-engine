package com.agentengine.runtime.actor;

import org.apache.pekko.actor.typed.ActorRef;

/**
 * Executes and resumes agent runs on behalf of a SessionActor.
 * Implementations offload execution to virtual threads.
 */
public interface AgentRunner {

    void startRun(
            SessionTopology topology,
            String runId,
            String message,
            ActorRef<SessionCommand> replyTo);

    void resumeRun(
            SessionTopology topology,
            String runId,
            String confirmationId,
            Object confirmationResponse,
            ActorRef<SessionCommand> replyTo);

    /** Re-executes the current run without modifying session state. Used for recoverable failures. */
    void retryRun(
            SessionTopology topology,
            String runId,
            ActorRef<SessionCommand> replyTo);
}
