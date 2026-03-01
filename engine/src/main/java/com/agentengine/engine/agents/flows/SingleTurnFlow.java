package com.agentengine.engine.agents.flows;

import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;

import java.util.List;
import java.util.Optional;

/**
 * Runs exactly one LLM round-trip regardless of the outer flow's maxSteps.
 */
final class SingleTurnFlow extends SingleFlow {
    public SingleTurnFlow(final List<RequestProcessor> reqs, final List<ResponseProcessor> resps) {
        super(reqs, resps, Optional.of(1));
    }
}
