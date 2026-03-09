package com.agentengine.engine.context;

public final class NoOpContextManager extends BaseContextManager {
  public static final NoOpContextManager INSTANCE = new NoOpContextManager();

  private NoOpContextManager() {
    super(contents -> contents);
  }
}
