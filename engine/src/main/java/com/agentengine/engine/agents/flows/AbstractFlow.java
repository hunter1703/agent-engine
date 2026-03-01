package com.agentengine.engine.agents.flows;

import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Optional;

public abstract class AbstractFlow extends SingleFlow {
    private final int maxSteps;
    private int stepsCompleted;

    public AbstractFlow(final int maxSteps, final List<RequestProcessor> requestProcessors, final List<ResponseProcessor> responseProcessors) {
        super(requestProcessors, responseProcessors, Optional.of(maxSteps));
        this.maxSteps = maxSteps;
    }

    @Override
    public Flowable<Event> run(final InvocationContext invocationContext) {
        stepsCompleted = 0;
        return runWithContinuation(invocationContext);
    }

    private Flowable<Event> runWithContinuation(final InvocationContext invocationContext) {
        final Flowable<Event> currentStepEvents =
            new StepFlow(requestProcessors, responseProcessors).run(invocationContext).cache();

        if (++stepsCompleted >= maxSteps) {
            return currentStepEvents;
        }

        return currentStepEvents.concatWith(
            currentStepEvents
                .toList()
                .flatMapPublisher(
                    eventList -> {
                        if (eventList.isEmpty()) {
                            return Flowable.empty();
                        }
                        final Event lastEvent = eventList.getLast();
                        if (shouldTerminate(lastEvent)) {
                            return Flowable.empty();
                        }
                        return Flowable.defer(() -> runWithContinuation(invocationContext));
                    }));
    }

    private static boolean shouldTerminate(final Event lastEvent) {
        if (lastEvent.actions().endInvocation().orElse(false)) {
            return true;
        }
        final boolean turnComplete = lastEvent.turnComplete().orElse(true);
        return lastEvent.finalResponse() && turnComplete;
    }

    private static final class StepFlow extends SingleFlow {
        private StepFlow(final List<RequestProcessor> requestProcessors, final List<ResponseProcessor> responseProcessors) {
            super(requestProcessors, responseProcessors, Optional.of(1));
        }
    }
}
