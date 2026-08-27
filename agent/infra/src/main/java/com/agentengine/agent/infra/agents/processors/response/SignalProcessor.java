package com.agentengine.agent.infra.agents.processors.response;

import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.agent.infra.utils.Signal;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts queued signals — violations to correct, or plain status updates — into persisted events
 * as soon as they're known, immediately after the model response that produced them.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li>All queued signals are converted into events in the same step, before the turn can pause or
 *       terminate — a signal added on a turn's last step (the common case for a guardrail or
 *       validation check on a final answer) is never left stranded only in-memory.
 *   <li>After signals are processed, the queue is cleared for the next turn.
 *   <li>If no signals are present, the response passes through unchanged.
 *   <li>Signal content is added as user-authored events; the next request picks them up as history
 *       via the standard request-content processor, so no request mutation is needed here.
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 *   <li>Session state must be initialized in {@code SessionUtils.initSessionState(context)}.
 *   <li>Plugins record signals from {@code afterModelCallback}, which runs before this processor in
 *       the same step.
 * </ul>
 */
public final class SignalProcessor implements ResponseProcessor {
  public static final SignalProcessor INSTANCE = new SignalProcessor();

  private static final Logger LOG = LoggerFactory.getLogger(SignalProcessor.class);

  private SignalProcessor() {}

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    final RunState runState = RunUtils.getRunState(context);
    final Set<Signal<?>> signals = runState.signals();

    if (CollectionUtils.isEmpty(signals)) {
      return Single.just(ResponseProcessingResult.create(response, List.of()));
    }

    LOG.info("Gathered {} signal(s) for delivery", signals.size());

    final List<Violation> violations = new ArrayList<>();
    final List<String> textUpdates = new ArrayList<>();
    for (final Signal<?> signal : signals) {
      LOG.debug("Signal: {}", signal);
      if (signal.context() instanceof Violation violation) {
        violations.add(violation);
      } else {
        textUpdates.add(String.valueOf(signal.context()));
      }
    }

    final List<Event> signalEvents = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(violations)) {
      signalEvents.add(EventUtils.buildCorrectiveEvent(context, violations));
    }
    if (CollectionUtils.isNotEmpty(textUpdates)) {
      signalEvents.add(EventUtils.buildUserTextEvent(context, String.join("\n\n", textUpdates)));
    }

    runState.clearSignals();
    return Single.just(ResponseProcessingResult.create(response, signalEvents));
  }
}
