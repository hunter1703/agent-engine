package com.agentengine.engine.agents.flows;

import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;

/**
 * Runs a single logical turn, retrying until the turn completes.
 */
final class SingleTurnFlow extends AbstractFlow {
    public SingleTurnFlow(final List<RequestProcessor> requestProcessors, final List<ResponseProcessor> responseProcessors) {
        super(Integer.MAX_VALUE, requestProcessors, responseProcessors, SingleTurnFlow::shouldTerminate);
    }

    private static boolean shouldTerminate(final Event event) {
        return event.actions().endInvocation().orElse(false)
            || event.turnComplete().orElse(true);
    }
}
