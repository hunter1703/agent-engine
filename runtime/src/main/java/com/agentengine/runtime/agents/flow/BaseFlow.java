package com.agentengine.runtime.agents.flow;

import com.agentengine.runtime.agents.processors.request.CorrectionProcessor;
import com.agentengine.runtime.agents.processors.request.PlanningRequestProcessor;
import com.agentengine.runtime.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.runtime.agents.processors.response.ResponseFormatValidationProcessor;
import com.agentengine.runtime.agents.processors.response.ToolCallSanitizationResponseProcessor;
import com.agentengine.runtime.utils.RunState;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.AgentTransfer;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.impl.ConcurrentHashSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default agent flow orchestration for Agent Engine.
 *
 * <p>Extends ADK's {@link SingleFlow} with a custom pipeline of request and response processors
 * that enforce protocol semantics, handle tool calls, and manage turn completion, loop termination,
 * and HITL pauses.
 *
 * <p>The parent {@code BaseLlmFlow} is configured with {@code maxSteps=1} so each {@code
 * super.run()} executes exactly one ADK step (one LLM call + tool execution). Events stream
 * through immediately for low-latency UX. After the step completes, a synthetic boundary event
 * is appended to commit the turn and signal the outcome.
 *
 * <h3>Step outcomes</h3>
 *
 * <ul>
 *   <li><b>PAUSED:</b> An {@code adk_request_confirmation} event is present — boundary carries
 *       {@code turnComplete=true}, no {@code finishReason}.
 *   <li><b>TERMINAL:</b> Turn limit reached, final answer (without continuation), or {@code
 *       endInvocation=true} — boundary carries {@code turnComplete=true} and {@code
 *       finishReason=STOP}.
 *   <li><b>CONTINUE:</b> Tool-call step or continuation-driven retry — boundary carries {@code
 *       turnComplete=true}, no {@code finishReason}. Loop recurses.
 * </ul>
 */
public final class BaseFlow extends SingleFlow {
    private static final Logger LOG = LoggerFactory.getLogger(BaseFlow.class);
    private static final ImmutableList<RequestProcessor> REQUEST_PROCESSORS = ImmutableList.<RequestProcessor>builder()
            .addAll(SingleFlow.REQUEST_PROCESSORS)
            .add(new AgentTransfer())
            .add(CorrectionProcessor.INSTANCE)
            .add(PlanningRequestProcessor.INSTANCE)
            .build();

    private static final ImmutableList<ResponseProcessor> RESPONSE_PROCESSORS =
            ImmutableList.<ResponseProcessor>builder()
                    .add(ToolCallSanitizationResponseProcessor.INSTANCE)
                    .add(ResponseFormatValidationProcessor.INSTANCE)
                    .add(PlanLoopResponseProcessor.INSTANCE)
                    .addAll(SingleFlow.RESPONSE_PROCESSORS)
                    .build();

    private final int maxSteps;

    public BaseFlow(final Integer maxSteps) {
        super(REQUEST_PROCESSORS, RESPONSE_PROCESSORS, Optional.of(1));
        this.maxSteps = maxSteps == null ? Integer.MAX_VALUE : maxSteps;
    }

    @Override
    public Flowable<Event> run(final InvocationContext invocationContext) {
        return runLoop(invocationContext);
    }

    private Flowable<Event> runLoop(final InvocationContext invocationContext) {
        final RunState runState = RunUtils.getOrInitState(invocationContext);
        final boolean mayContinue = runState.consumeTurn(maxSteps);
        if (!mayContinue) {
            return Flowable.empty();
        }

        final AtomicBoolean endInvocation = new AtomicBoolean();
        // Reset per-turn flags so a final answer from a previous continuation turn
        // does not bleed into the current turn's outcome resolution.
        final AtomicBoolean foundFinalAnswer = new AtomicBoolean();
        // original tool call id that requested the confirmation
        final ConcurrentHashSet<String> confirmationRequested = new ConcurrentHashSet<>();
        return super.run(invocationContext)
                .map(event -> {
                    // ADK may emit events with null IDs (e.g. adk_request_confirmation); assign a
                    // generated ID to provide reliable guarantee
                    if (StringUtils.isBlank(event.id())) {
                        event = event.toBuilder().id(Event.generateEventId()).build();
                    }
                    confirmationRequested.addAll(
                            event.actions().requestedToolConfirmations().keySet());
                    final Content content =
                            event.content().orElse(Content.builder().build());

                    // every tool needs to return something, even if it requires confirmation; tools that require
                    // confirmation can emit empty response, so filter out before emitting to the stream for correctness
                    final List<Part> filteredParts = content.parts().orElse(List.of()).stream()
                            .filter(part -> {
                                final FunctionResponse response =
                                        part.functionResponse().orElse(null);
                                return response == null
                                        || !confirmationRequested.contains(
                                                response.id().orElse(null))
                                        || CollectionUtils.isNotEmpty(
                                                response.response().orElse(Map.of()));
                            })
                            .toList();

                    if (event.actions().endInvocation().orElse(false)) {
                        endInvocation.set(true);
                    }
                    // Only model/agent events count as a final answer — user events (e.g. corrective
                    // messages injected by CorrectionProcessor) must not set this flag
                    if (!Constants.AUTHOR_USER.equals(event.author()) && event.finalResponse()) {
                        foundFinalAnswer.set(true);
                    }
                    // Strip turnComplete and finishReason — the synthetic boundary event is the
                    // sole authoritative commit point for the turn.
                    return event.toBuilder()
                            .content(content.toBuilder().parts(filteredParts).build())
                            .turnComplete(Optional.empty())
                            .finishReason(Optional.empty())
                            .build();
                })
                .concatWith(Flowable.defer(() -> {
                    final TurnOutcome outcome = resolveOutcome(
                            endInvocation.get(),
                            CollectionUtils.isNotEmpty(confirmationRequested),
                            foundFinalAnswer.get(),
                            runState.consumeContinuation());
                    final Flowable<Event> turnCompletedEvent =
                            Flowable.just(getTurnCompletedEvent(invocationContext, outcome));
                    return outcome == TurnOutcome.CONTINUE
                            ? turnCompletedEvent.concatWith(Flowable.defer(() -> runLoop(invocationContext)))
                            : turnCompletedEvent;
                }))
                .doOnNext(event -> LOG.info("baseflow : {}", JsonUtils.toJson(event)));
    }

    private static TurnOutcome resolveOutcome(
            final boolean endInvocation,
            final boolean confirmationRequested,
            final boolean foundFinalAnswer,
            final boolean continuationRequested) {
        if (endInvocation) {
            return TurnOutcome.TERMINAL;
        }
        // ADK guarantee: when the LLM issues multiple tool calls in one turn, Functions merges all
        // individual tool responses into a single event (mergeParallelFunctionResponseEvents) and
        // then generates at most one adk_request_confirmation event containing N confirmation
        // function calls — one per tool that called toolContext.requestConfirmation().
        if (confirmationRequested) {
            return TurnOutcome.PAUSED;
        }
        if (foundFinalAnswer) {
            return continuationRequested ? TurnOutcome.CONTINUE : TurnOutcome.TERMINAL;
        }
        return TurnOutcome.CONTINUE;
    }

    // Synthetic event appended after each turn to provide the single, authoritative commit point.
    // All preceding turn events have turnComplete/finishReason stripped, so only this event controls
    // the commit decision.
    private static Event getTurnCompletedEvent(final InvocationContext invocationContext, final TurnOutcome outcome) {
        final Event.Builder builder = Event.builder()
                .invocationId(invocationContext.invocationId())
                .author(invocationContext.agent().name())
                .turnComplete(true);
        if (outcome == TurnOutcome.TERMINAL || outcome == TurnOutcome.PAUSED) {
            builder.finishReason(new FinishReason(FinishReason.Known.STOP));
        }
        return builder.id(UUID.randomUUID().toString()).build();
    }

    private enum TurnOutcome {
        CONTINUE,
        TERMINAL,
        PAUSED
    }
}
