package com.agentengine.util.common.exception;

/**
 * Thrown when a caller-supplied version does not match the stored version, meaning the entity was
 * modified since the caller read it. Only raised by the update overloads that take an explicit
 * version — callers that do not opt into version checking never see this.
 */
public class StaleStateException extends RuntimeException {

  private final String id;
  private final long expectedVersion;

  public StaleStateException(final String id, final long expectedVersion) {
    super("Stale state for entity with ID " + id + ": expected version=" + expectedVersion);
    this.id = id;
    this.expectedVersion = expectedVersion;
  }

  public String getId() {
    return id;
  }

  public long getExpectedVersion() {
    return expectedVersion;
  }
}
