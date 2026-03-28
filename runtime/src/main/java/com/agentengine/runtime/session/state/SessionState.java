package com.agentengine.runtime.session.state;

import com.agentengine.util.pekko.PekkoSerializable;

/** Minimal session lifecycle for a session actor. */
public enum SessionState implements PekkoSerializable {
  IDLE, RUNNING, PAUSED
}
