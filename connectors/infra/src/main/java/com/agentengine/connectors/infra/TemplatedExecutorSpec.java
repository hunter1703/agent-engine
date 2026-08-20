package com.agentengine.connectors.infra;

import com.agentengine.connectors.infra.beans.ExecutorSpec;

public abstract class TemplatedExecutorSpec<Spec extends ExecutorSpec, Params, Output> {
  protected final Spec spec;

  protected TemplatedExecutorSpec(Spec spec) {
    this.spec = spec;
  }

  public abstract Output evaluate(Params params);
}
