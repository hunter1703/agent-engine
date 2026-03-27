package com.agentengine.runtime.actor;

import org.apache.pekko.actor.typed.ActorRef;

/**
 * Executes and resumes agent runs on behalf of a SessionActor. Implementations
 * offload execution to virtual threads.
 */
public interface AgentRunner {

  void startRun(SessionTopology topology, String runId, String message, ActorRef<SessionCommand> replyTo);

  void resumeRun(SessionTopology topology, String runId, String confirmationId, Object confirmationResponse,
      ActorRef<SessionCommand> replyTo);

  /**
   * Re-executes the current run without modifying session state. Used for
   * recoverable failures.
   */
  void retryRun(SessionTopology topology, String runId, ActorRef<SessionCommand> replyTo);

  /**
   * Initializes and starts a new child session actor, then notifies the parent
   * actor when the child run completes or fails.
   */
  void spawnChild(SessionTopology parentTopology, String childAgentId, String childSessionId, String childRunId, String message,
      ActorRef<SessionCommand> parentRef);

  /**
   * Sends a new task to an already-initialized child session actor.
   */
  void sendChildTask(SessionTopology parentTopology, String childAgentId, String childSessionId, String childRunId, String message,
      ActorRef<SessionCommand> parentRef);

  /**
   * Notifies the parent actor that a child run has completed. No-op if
   * childTopology is a root session.
   */
  void notifyParentOfCompletion(SessionTopology childTopology, String runId, ChildRegistry.ChildRunResult result);
}
