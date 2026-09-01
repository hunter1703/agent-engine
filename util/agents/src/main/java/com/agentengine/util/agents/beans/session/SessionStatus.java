package com.agentengine.util.agents.beans.session;

/** Lifecycle status of an agent session as visible in the catalog. */
public enum SessionStatus {
  UNKNOWN,
  /** The session has been created but has not yet started processing its first turn. */
  INIT,
  RUNNING,
  /** The session is paused, awaiting human input to resume. */
  PAUSED,
  /** The session has produced a final answer and is no longer active. */
  COMPLETED,
  /** The session terminated with an unrecoverable error. */
  FAILED;

  public static SessionStatus valueOfOrDefault(final String value) {
    if (value == null) {
      return UNKNOWN;
    }
    try {
      return valueOf(value);
    } catch (final IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
