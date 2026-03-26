package com.agentengine.runtime.actor;

import org.apache.pekko.actor.typed.ActorRef;

/** Executes and resumes agent runs on behalf of a {@link SessionActor}. */
public interface AgentRunner {

    void startRun(String agentId,
                  String sessionId,
                  String rootSessionId,
                  String parentSessionId,
                  String parentAgentId,
                  String runId,
                  String message,
                  ActorRef<SessionActor.Command> replyTo);

    void resumeRun(String agentId,
                   String sessionId,
                   String runId,
                   String confirmationId,
                   boolean confirmed,
                   String answer,
                   ActorRef<SessionActor.Command> replyTo);

    /** Re-executes the current run without modifying session state. Used for recoverable failures. */
    void retryRun(String agentId,
                  String sessionId,
                  String runId,
                  ActorRef<SessionActor.Command> replyTo);
}
