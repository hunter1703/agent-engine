package com.agentengine.engine.agents.flows;

import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import java.util.List;
import java.util.Optional;

public abstract class AbstractFlow extends SingleFlow {
    public AbstractFlow(final int maxSteps, final List<RequestProcessor> requestProcessors, final List<ResponseProcessor> responseProcessors) {
        super(requestProcessors, responseProcessors, Optional.of(maxSteps));
    }
}
