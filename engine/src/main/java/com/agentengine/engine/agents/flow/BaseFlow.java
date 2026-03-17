package com.agentengine.engine.agents.flow;

import com.agentengine.engine.agents.processors.request.CorrectionProcessor;
import com.agentengine.engine.agents.processors.request.PlanningRequestProcessor;
import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.agents.processors.response.ToolCallSanitizationResponseProcessor;
import com.agentengine.engine.agents.processors.response.TurnCompletionResponseProcessor;
import com.agentengine.engine.utils.EventUtils;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.AgentTransfer;
import com.google.genai.types.FinishReason;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import com.google.common.collect.ImmutableList;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BaseFlow extends SingleFlow {
  private static final ImmutableList<RequestProcessor> REQUEST_PROCESSORS = ImmutableList.<RequestProcessor>builder()
      .addAll(SingleFlow.REQUEST_PROCESSORS).add(new AgentTransfer()).add(CorrectionProcessor.INSTANCE)
      .add(PlanningRequestProcessor.INSTANCE).build();

  private static final ImmutableList<ResponseProcessor> RESPONSE_PROCESSORS = ImmutableList.<ResponseProcessor>builder()
      .add(ToolCallSanitizationResponseProcessor.INSTANCE).add(PlanLoopResponseProcessor.INSTANCE).addAll(SingleFlow.RESPONSE_PROCESSORS)
      .build();

  public BaseFlow(final Integer maxSteps) {
    super(REQUEST_PROCESSORS, CollectionUtils.append(RESPONSE_PROCESSORS, new TurnCompletionResponseProcessor(maxSteps)),
        Optional.of(Integer.MAX_VALUE));
  }

  @Override
  public Flowable<Event> run(final InvocationContext invocationContext) {
    return runLoop(invocationContext);
  }

  /**
   * Runs the agent flow, looping until a terminal event is observed.
   *
   * <h3>Termination ownership (two sources, intentionally split)</h3>
   *
   * <p><b>{@link TurnCompletionResponseProcessor}</b> owns termination for
   * LLM-generated responses: it sets {@code finishReason=STOP} on final-answer
   * events when no continuation was requested, and omits it when continuation
   * was requested. ADK's response-processor chain only sees {@code LlmResponse}
   * objects, so this is the only place that can act on LLM output.
   *
   * <p><b>This method</b> owns termination for non-LLM events — specifically
   * function-response events produced by tool execution. When a tool sets
   * {@code endInvocation=true} (e.g. {@code HumanInTheLoopTool}), ADK stops its
   * own inner loop but emits the event without a {@code finishReason}. No ADK
   * extension point exists between tool execution and event emission where
   * {@code finishReason} could be set cleanly, so this method normalises the
   * signal: if an event carries {@code endInvocation=true} and no
   * {@code finishReason}, it stamps {@code finishReason=STOP} and
   * {@code turnComplete=true} before the event reaches downstream consumers.
   *
   * <p>Terminal events without {@code finishReason} (continuation case) are
   * marked internal so they are excluded from user-facing output while remaining
   * in session history.
   */
  private Flowable<Event> runLoop(final InvocationContext invocationContext) {
    final AtomicBoolean finished = new AtomicBoolean(false);
    return super.run(invocationContext).doOnNext(event -> {
      if (event.actions().endInvocation().orElse(false) && event.finishReason().isEmpty()) {
        event.setTurnComplete(Optional.of(true));
        event.setFinishReason(Optional.of(new FinishReason(FinishReason.Known.STOP)));
      }
      if (event.finishReason().isPresent()) {
        finished.set(true);
      } else if (event.finalResponse()) {
        EventUtils.markAsInternal(event);
      }
    }).concatWith(Flowable.defer(() -> finished.get() ? Flowable.empty() : runLoop(invocationContext)));
  }
}
