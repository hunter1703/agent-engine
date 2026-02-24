package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractFlow extends SingleFlow {
    private final Set<String> activeInvocations = ConcurrentHashMap.newKeySet();

    public AbstractFlow(final int maxSteps, final List<RequestProcessor> requestProcessors, final List<ResponseProcessor> responseProcessors) {
        super(requestProcessors, responseProcessors, Optional.of(maxSteps));
    }

    @Override
    public Flowable<Event> run(final InvocationContext invocationContext) {
        return runWithReset(invocationContext, super.run(invocationContext));
    }

    @Override
    public Flowable<Event> runLive(final InvocationContext invocationContext) {
        return runWithReset(invocationContext, super.runLive(invocationContext));
    }

    private Flowable<Event> runWithReset(
            final InvocationContext invocationContext, final Flowable<Event> flowable) {
        final String invocationId = invocationContext.invocationId();
        final boolean firstInvocation = activeInvocations.add(invocationId);
        if (firstInvocation) {
            stepsCompleted = 0;
            return flowable.doFinally(() -> activeInvocations.remove(invocationId));
        }
        return flowable;
    }
}
