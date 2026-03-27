package com.agentengine.runtime.actor;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.pekko.events.PekkoEventChannel;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.SpawnProtocol;

/**
 * Scope-keyed channel for live session events where each {@code rootSessionId}
 * maps to an independent stream.
 */
@Singleton
public class SessionEventChannel extends PekkoEventChannel<String, SessionEvent> {

  public SessionEventChannel(final ActorSystem<SpawnProtocol.Command> system) {
    super(system, "session-events");
  }
}
