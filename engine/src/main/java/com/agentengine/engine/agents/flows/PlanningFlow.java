package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.utils.CorrectionProcessor;
import com.agentengine.engine.utils.FinalAnswerResponseProcessor;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import java.util.ArrayList;
import java.util.List;

public class PlanningFlow extends AbstractFlow {

  public PlanningFlow(
      final int maxStepsPerTask,
      final List<RequestProcessor> requestProcessors,
      final List<ResponseProcessor> responseProcessors) {
    super(maxStepsPerTask,
        buildRequests(requestProcessors),
        buildResponses(maxStepsPerTask, responseProcessors));
  }

  private static List<RequestProcessor> buildRequests(final List<RequestProcessor> provided) {
    final List<RequestProcessor> requestProcessors = new ArrayList<>(SingleFlow.REQUEST_PROCESSORS);
    requestProcessors.addAll(CollectionUtils.nullSafeList(provided));
    requestProcessors.add(PlanningRequestProcessor.INSTANCE);
    requestProcessors.add(FinalAnswerRequestProcessor.INSTANCE);
    requestProcessors.add(CorrectionProcessor.INSTANCE);
    requestProcessors.add(LoggingRequestProcessor.INSTANCE);
    return requestProcessors;
  }

  private static List<ResponseProcessor> buildResponses(final int maxSteps, final List<ResponseProcessor> provided) {
    final List<ResponseProcessor> responseProcessors = new ArrayList<>();
    responseProcessors.add(FinalAnswerResponseProcessor.INSTANCE);
    responseProcessors.addAll(CollectionUtils.nullSafeList(provided));
    responseProcessors.add(new PlanLoopResponseProcessor(maxSteps));
    responseProcessors.add(new RedundantToolCallsResponseProcessor());
    responseProcessors.addAll(SingleFlow.RESPONSE_PROCESSORS);
    return responseProcessors;
  }
}
