package com.agentengine.runtime.session.commands;

import com.agentengine.runtime.actor.ResumeResult;
import com.agentengine.runtime.session.StartChildResult;
import com.agentengine.runtime.actor.StartSessionResult;
import com.google.adk.events.Event;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;

public interface InternalCommand extends SessionCommand {

  record PublishEventCommand(Event event) implements InternalCommand {
  }

  record PauseCommand(String childSessionId, String confirmationId, ActorRef<Done> replyTo) implements InternalCommand {
    public static PauseCommand self(final String confirmationId, final ActorRef<Done> replyTo) {
      return new PauseCommand(null, confirmationId, replyTo);
    }

    public static PauseCommand child(final String childSessionId, final String confirmationId, final ActorRef<Done> replyTo) {
      return new PauseCommand(childSessionId, confirmationId, replyTo);
    }

    public boolean forChild() {
      return childSessionId != null && !childSessionId.isBlank();
    }
  }

  record RunFailedCommand(String error) implements InternalCommand {
  }

  record ResumeChildCommand(String confirmationId, ActorRef<ResumeResult> replyTo, ResumeResult result,
      String error) implements InternalCommand {
  }

  record StartChildCompletedCommand(String sessionId, String agentId, ActorRef<StartChildResult> replyTo, StartSessionResult result,
      String error) implements InternalCommand {
  }

  record StartNextQueuedMessageCommand() implements InternalCommand {
  }
}
