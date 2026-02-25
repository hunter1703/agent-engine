package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;

import java.util.ArrayList;
import java.util.List;

public class PlanningFlow extends AbstractFlow {
  private static final List<RequestProcessor> DEFAULT_REQUEST_PROCESSORS = new ArrayList<>();
  private static final List<ResponseProcessor> DEFAULT_RESPONSE_PROCESSORS = new ArrayList<>();

  static {
    DEFAULT_REQUEST_PROCESSORS.addAll(SingleFlow.REQUEST_PROCESSORS);
    DEFAULT_REQUEST_PROCESSORS.add(new PlanContextRequestProcessor());
    DEFAULT_REQUEST_PROCESSORS.add(new PlanTaskRequestProcessor());
    DEFAULT_RESPONSE_PROCESSORS.add(new RedundantToolCallsResponseProcessor());
    DEFAULT_RESPONSE_PROCESSORS.addAll(SingleFlow.RESPONSE_PROCESSORS);
  }

  public PlanningFlow(
      final int maxStepsPerTask,
      final List<RequestProcessor> requestProcessors,
      final List<ResponseProcessor> responseProcessors) {
      final List<ResponseProcessor> resProcessors = new ArrayList<>(responseProcessors);
    resProcessors.add(new PlanLoopResponseProcessor(maxStepsPerTask));
    resProcessors.addAll(DEFAULT_RESPONSE_PROCESSORS);

    super(maxStepsPerTask, CollectionUtils.append(DEFAULT_REQUEST_PROCESSORS, requestProcessors, LoggingRequestProcessor.INSTANCE), resProcessors);
  }
}
