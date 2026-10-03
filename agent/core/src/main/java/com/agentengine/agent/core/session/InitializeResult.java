package com.agentengine.agent.core.session;

import com.agentengine.util.pekko.PekkoSerializable;

/**
 * Reply to a session initialization: whether the session's id belongs to a deleted session, which
 * can never be used again.
 */
public record InitializeResult(boolean deleted) implements PekkoSerializable {

  public static final InitializeResult INITIALIZED = new InitializeResult(false);
  public static final InitializeResult DELETED = new InitializeResult(true);
}
