package com.agentengine.engine.guardrails;

public final class GuardrailBlockedException extends RuntimeException {
  public GuardrailBlockedException(final String message) {
    super(message);
  }
}
