package com.agentengine.engine.agents.flow;

import com.agentengine.engine.agents.processors.request.CorrectionProcessor;
import com.agentengine.engine.agents.processors.request.PlanningRequestProcessor;
import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.agents.processors.response.ToolCallSanitizationResponseProcessor;
import com.agentengine.engine.agents.processors.response.RunCompletionResponseProcessor;
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

/**
 * The default agent flow orchestration for Agent Engine.
 *
 * <p>
 * Extends ADK's {@link SingleFlow} with a custom pipeline of request and
 * response processors that enforce protocol semantics, handle tool calls, and
 * manage turn completion and loop termination.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 * <li><b>Event loop termination:</b> The flow emits events until a terminal
 * event is observed. A terminal event has {@code finishReason} set.
 * <li><b>Tool execution events:</b> When a tool sets
 * {@code endInvocation=true}, the resulting event will have
 * {@code finishReason=STOP} (if not already present) and
 * {@code turnComplete=true}.
 * <li><b>Continuation:</b> If an event is terminal but has no
 * {@code finishReason}, the flow continues (loops again). Such events may
 * appear in session history but are marked as internal (implementation detail
 * for session tracking).
 * <li><b>Turn limit:</b> If configured with a turn limit, the flow halts with
 * {@code finishReason=STOP} after that many turns.
 * </ul>
 */
public final class BaseFlow extends SingleFlow {
  private static final ImmutableList<RequestProcessor> REQUEST_PROCESSORS = ImmutableList.<RequestProcessor>builder()
      .addAll(SingleFlow.REQUEST_PROCESSORS).add(new AgentTransfer()).add(CorrectionProcessor.INSTANCE)
      .add(PlanningRequestProcessor.INSTANCE).build();

  private static final ImmutableList<ResponseProcessor> RESPONSE_PROCESSORS = ImmutableList.<ResponseProcessor>builder()
      .add(ToolCallSanitizationResponseProcessor.INSTANCE).add(PlanLoopResponseProcessor.INSTANCE).addAll(SingleFlow.RESPONSE_PROCESSORS)
      .build();

  public BaseFlow(final Integer maxSteps) {
    super(REQUEST_PROCESSORS, CollectionUtils.append(RESPONSE_PROCESSORS, new RunCompletionResponseProcessor(maxSteps)),
        Optional.of(Integer.MAX_VALUE));
  }

  @Override
  public Flowable<Event> run(final InvocationContext invocationContext) {
    return runLoop(invocationContext);
  }

  /**
   * Runs the agent flow, looping until a terminal event is observed.
   *
   * <p>
   * The flow emits events in sequence. When a terminal event (one with
   * {@code finishReason} set) is observed, the loop terminates. Tool execution
   * events with {@code endInvocation=true} are normalized to include
   * {@code finishReason=STOP} and {@code turnComplete=true}.
   *
   * <p>
   * Terminal events without {@code finishReason} (used to signal continuation)
   * are marked internal so they remain in session history but are excluded from
   * user-facing output.
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
