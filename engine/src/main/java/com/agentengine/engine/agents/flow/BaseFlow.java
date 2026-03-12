package com.agentengine.engine.agents.flow;

import com.agentengine.engine.agents.processors.request.CorrectionProcessor;
import com.agentengine.engine.agents.processors.request.PlanningRequestProcessor;
import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.agents.processors.response.ToolCallSanitizationResponseProcessor;
import com.agentengine.engine.agents.processors.response.TurnCompletionResponseProcessor;
import com.agentengine.engine.api.utils.EventUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.AgentTransfer;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import com.google.common.collect.ImmutableList;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BaseFlow extends SingleFlow {
  private static final ImmutableList<RequestProcessor> REQUEST_PROCESSORS =
      ImmutableList.<RequestProcessor>builder()
          .addAll(SingleFlow.REQUEST_PROCESSORS)
          .add(new AgentTransfer())
          .add(CorrectionProcessor.INSTANCE)
          .add(PlanningRequestProcessor.INSTANCE)
          .build();

  private static final ImmutableList<ResponseProcessor> RESPONSE_PROCESSORS =
      ImmutableList.<ResponseProcessor>builder()
          .add(ToolCallSanitizationResponseProcessor.INSTANCE)
          .add(PlanLoopResponseProcessor.INSTANCE)
          .addAll(SingleFlow.RESPONSE_PROCESSORS)
          .add(TurnCompletionResponseProcessor.INSTANCE)
          .build();

  public BaseFlow(final Integer maxSteps) {
    super(REQUEST_PROCESSORS, RESPONSE_PROCESSORS, Optional.ofNullable(maxSteps));
  }

  @Override
  public Flowable<Event> run(final InvocationContext invocationContext) {
    return runLoop(invocationContext, Integer.MAX_VALUE);
  }

  /**
   * Runs the agent flow, looping if the run completed without a {@code finishReason} event.
   *
   * <p>{@link TurnCompletionResponseProcessor} sets {@code finishReason} on terminal events when
   * no continuation was requested, and omits it when continuation was requested. BaseFlow observes
   * this signal: no {@code finishReason} seen → loop; {@code finishReason} seen → done.
   *
   * <p>Terminal events without {@code finishReason} (continuation case) are marked internal so
   * they are excluded from user-facing output while remaining in session history.
   */
  private Flowable<Event> runLoop(final InvocationContext invocationContext, final int remainingLoops) {
    final AtomicBoolean finished = new AtomicBoolean(false);
    return super.run(invocationContext)
        .doOnNext(event -> {
          if (event.finishReason().isPresent()) {
            finished.set(true);
          } else if (event.finalResponse()) {
            EventUtils.markAsInternal(event);
          }
        })
        .concatWith(
            Flowable.defer(
                () ->
                    (finished.get() || remainingLoops <= 0)
                        ? Flowable.empty()
                        : runLoop(invocationContext, remainingLoops - 1)));
  }
}
