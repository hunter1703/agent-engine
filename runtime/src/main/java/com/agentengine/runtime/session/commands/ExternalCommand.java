package com.agentengine.runtime.session.commands;

import com.agentengine.runtime.actor.ResumeResult;
import com.agentengine.runtime.session.StartChildResult;
import com.agentengine.runtime.actor.StartSessionResult;
import com.agentengine.runtime.session.SessionActor;
import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.runtime.session.state.SessionTopology;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;

/** Public commands accepted by {@link SessionActor}. */
public interface ExternalCommand extends SessionCommand {

  record InitializeCommand(SessionTopology topology, ActorRef<Done> replyTo) implements ExternalCommand {
  }

  record StartCommand(String message, ActorRef<StartSessionResult> replyTo) implements ExternalCommand {
  }

  record StartChildCommand(String agentId, String message, ActorRef<StartChildResult> replyTo) implements ExternalCommand {
  }

  record SendMessageCommand(String sessionId, String message, ActorRef<StartSessionResult> replyTo) implements ExternalCommand {
  }

  record ResumeCommand(String confirmationId, Boolean confirmed, String answer, ActorRef<ResumeResult> replyTo) implements ExternalCommand {
  }

  record AwaitCommand(String childSessionId, ActorRef<RunResult> replyTo) implements ExternalCommand {
  }
}
