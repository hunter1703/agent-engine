package com.agentengine.engine.agents.flows;

import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.agents.processors.request.*;
import com.agentengine.engine.agents.processors.response.*;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.beans.config.OutputEvaluationConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.guardrails.Guardrail;
import com.agentengine.engine.utils.HitlStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import io.reactivex.rxjava3.core.Flowable;

import java.util.ArrayList;
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
                .concatWith(
                    Flowable.defer(
                        () ->
                            terminated.get() || HitlStateUtils.isPaused(invocationContext)
                                ? Flowable.empty()
                                : runWithContinuation(invocationContext)));
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


    protected static List<RequestProcessor> buildRequests(final Parser parser) {
        final List<RequestProcessor> requestProcessors = new ArrayList<>();
        requestProcessors.add(RunInitRequestProcessor.INSTANCE);
        requestProcessors.add(HumanInTheLoopRequestProcessor.INSTANCE);
        requestProcessors.addAll(SingleFlow.REQUEST_PROCESSORS);
        requestProcessors.add(CorrectionProcessor.INSTANCE);
        requestProcessors.add(PlanningRequestProcessor.INSTANCE);
        requestProcessors.add(parser);
        requestProcessors.add(LoggingRequestProcessor.INSTANCE);
        return requestProcessors;
    }

    protected static List<ResponseProcessor> buildResponses(
            final Parser parser,
            final List<Guardrail> outputGuardrails,
            final GuardrailErrorMode guardrailErrorMode,
            final OutputEvaluationConfig outputEvaluationConfig) {
        final List<ResponseProcessor> responseProcessors = new ArrayList<>();
        responseProcessors.add(parser);
        if (CollectionUtils.isNotEmpty(outputGuardrails)) {
            responseProcessors.add(new GuardrailResponseProcessor(outputGuardrails, guardrailErrorMode));
        }
        responseProcessors.add(PlanLoopResponseProcessor.INSTANCE);
        if (outputEvaluationConfig != null && outputEvaluationConfig.isEnabled()) {
            responseProcessors.add(new OutputEvaluationResponseProcessor(outputEvaluationConfig));
        }
        responseProcessors.add(RedundantToolCallsResponseProcessor.INSTANCE);
        responseProcessors.add(TurnCompletionResponseProcessor.INSTANCE);
        responseProcessors.addAll(SingleFlow.RESPONSE_PROCESSORS);
        responseProcessors.add(PartOrderingResponseProcessor.INSTANCE);
        responseProcessors.add(RunCleanupResponseProcessor.INSTANCE);
        return responseProcessors;
    }
}
