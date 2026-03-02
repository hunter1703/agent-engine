package com.agentengine.engine.agents.flows;

import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class AbstractFlow extends SingleFlow {
    private final int maxSteps;
    private int stepsCompleted;
    private final Predicate<Event> shouldTerminate;

    public AbstractFlow(final int maxSteps, final List<RequestProcessor> requestProcessors, final List<ResponseProcessor> responseProcessors) {
        super(requestProcessors, responseProcessors, Optional.of(maxSteps));
        this.maxSteps = maxSteps;
        this.shouldTerminate = AbstractFlow::shouldTerminate;
    }

    public AbstractFlow(final int maxSteps, final List<RequestProcessor> requestProcessors, final List<ResponseProcessor> responseProcessors, final Predicate<Event> shouldTerminate) {
        super(requestProcessors, responseProcessors, Optional.of(maxSteps));
        this.maxSteps = maxSteps;
        this.shouldTerminate = shouldTerminate;
    }

    @Override
    public Flowable<Event> run(final InvocationContext invocationContext) {
        stepsCompleted = 0;
        return runWithContinuation(invocationContext);
    }

    private Flowable<Event> runWithContinuation(final InvocationContext invocationContext) {
        final Flowable<Event> events = super.run(invocationContext);
        if (++stepsCompleted >= maxSteps) {
            return events;
        }
        final AtomicBoolean terminated = new AtomicBoolean();
        return events.doOnNext(event -> terminated.set(shouldTerminate.test(event))).takeUntil(shouldTerminate::test)
                .concatWith(Flowable.defer(() -> terminated.get() ? Flowable.empty() : runWithContinuation(invocationContext)));
    }

    private static boolean shouldTerminate(final Event event) {
        if (event.actions().endInvocation().orElse(false)) {
            return true;
        }
        final boolean turnComplete = event.turnComplete().orElse(true);
        return event.finalResponse() && turnComplete;
    }

    public List<ResponseProcessor> getResponseProcessors() {
        return responseProcessors;
    }
}
