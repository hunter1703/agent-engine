package com.agentengine.engine.agents.processors.request;

import com.agentengine.engine.utils.CorrectionUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunUtils;
import com.agentengine.engine.utils.Violation;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects corrective prompts from prior violations into the request.
 *
 * <p>
 * When the model produces output that violates a protocol (e.g., tool calls in
 * partial responses), a violation is recorded with a correction message. This
 * processor retrieves those violations and appends their correction messages as
 * user content to the next request, providing the model with explicit feedback
 * on how to correct its behavior.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 * <li>All accumulated violations are converted into correction content and
 * appended to the request.
 * <li>After violations are processed, the violation list is cleared for the
 * next turn.
 * <li>If no violations are present, the request passes through unchanged.
 * <li>Correction content is added as user messages (role="user") to the
 * request.
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 * <li>Session violation state must be initialized in
 * {@code RunUtils.getOrInitState(context)}.
 * <li>Prior response processors have recorded any violations that need
 * correction.
 * </ul>
 */
public final class CorrectionProcessor implements RequestProcessor {
  public static final CorrectionProcessor INSTANCE = new CorrectionProcessor();

  private static final Logger LOG = LoggerFactory.getLogger(CorrectionProcessor.class);

  private CorrectionProcessor() {
  }

  @Override
  public Single<RequestProcessingResult> processRequest(final InvocationContext context, final LlmRequest request) {
    final RunState runState = RunUtils.getOrInitState(context);
    final List<Violation> violations = runState.violations();

    if (CollectionUtils.isEmpty(violations)) {
      return Single.just(RequestProcessingResult.create(request, List.of()));
    }

    LOG.info("Gathered {} violation(s) for correction", violations.size());

    final List<Event> correctiveEvents = new ArrayList<>();
    final List<Content> contents = CollectionUtils.nullSafeMutableList(request.contents());

    for (final Violation violation : violations) {
      LOG.debug("Violation: code={} message={} correction={}", violation.code(), violation.message(), violation.correctionMessage());
      final String correctionMessage = violation.correctionMessage();
      if (StringUtils.isBlank(correctionMessage)) {
        continue;
      }
      final Event correctiveEvent = CorrectionUtils.buildCorrectiveEvent(context, violation.code(), correctionMessage);
      correctiveEvent.content().ifPresent(contents::add);
      correctiveEvents.add(correctiveEvent);
    }
    runState.clearViolations();
    final LlmRequest updatedRequest = request.toBuilder().contents(contents).build();
    return Single.just(RequestProcessingResult.create(updatedRequest, correctiveEvents));
  }
}
