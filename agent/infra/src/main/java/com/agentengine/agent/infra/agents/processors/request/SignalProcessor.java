package com.agentengine.agent.infra.agents.processors.request;

import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.agent.infra.utils.Signal;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects queued signals — violations to correct, or plain status updates — into the request as
 * user-turn messages.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li>All queued signals are converted into content and appended to the request.
 *   <li>After signals are processed, the queue is cleared for the next turn.
 *   <li>If no signals are present, the request passes through unchanged.
 *   <li>Signal content is added as user messages (role="user") to the request.
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 *   <li>Session state must be initialized in {@code SessionUtils.initSessionState(context)}.
 *   <li>Prior response processors have recorded any signals that need delivering.
 * </ul>
 */
public final class SignalProcessor implements RequestProcessor {
  public static final SignalProcessor INSTANCE = new SignalProcessor();

  private static final Logger LOG = LoggerFactory.getLogger(SignalProcessor.class);

  private SignalProcessor() {}

  @Override
  public Single<RequestProcessingResult> processRequest(
      final InvocationContext context, final LlmRequest request) {
    final RunState runState = RunUtils.getRunState(context);
    final Set<Signal<?>> signals = runState.signals();

    if (CollectionUtils.isEmpty(signals)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    LOG.info("Gathered {} signal(s) for delivery", signals.size());

    final List<Event> signalEvents = new ArrayList<>();
    final List<Content> contents = CollectionUtils.nullSafeMutableList(request.contents());

    for (final Signal<?> signal : signals) {
      LOG.debug("Signal: {}", signal);
      final Event signalEvent =
          switch (signal.context()) {
            case Violation violation -> EventUtils.buildCorrectiveEvent(context, violation);
            default -> EventUtils.buildUserTextEvent(context, String.valueOf(signal.context()));
          };
      signalEvent.content().ifPresent(contents::add);
      signalEvents.add(signalEvent);
    }
    runState.clearSignals();
    final LlmRequest updatedRequest = request.toBuilder().contents(contents).build();
    return Single.just(RequestProcessingResult.create(updatedRequest, signalEvents));
  }
}
