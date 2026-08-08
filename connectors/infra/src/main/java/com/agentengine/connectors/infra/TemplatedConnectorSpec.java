package com.agentengine.connectors.infra;

import com.agentengine.connectors.infra.beans.ConnectorSpec;

public abstract class TemplatedConnectorSpec<Spec extends ConnectorSpec, Params, Output> {
    private final Spec spec;

    protected TemplatedConnectorSpec(Spec spec) {
        this.spec = spec;
    }

    public abstract Output evaluate(Params params);
}
