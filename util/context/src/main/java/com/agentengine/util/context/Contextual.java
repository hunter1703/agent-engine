package com.agentengine.util.context;

public interface Contextual {

  default Context context() {
    return null;
  }
}
