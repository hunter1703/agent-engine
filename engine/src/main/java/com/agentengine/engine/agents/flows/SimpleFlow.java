package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.utils.CorrectionProcessor;
import com.agentengine.engine.utils.FinalAnswerResponseProcessor;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import java.util.ArrayList;
import java.util.List;

public class SimpleFlow extends AbstractFlow {

  public SimpleFlow(
      final int maxSteps,
      final List<RequestProcessor> requestProcessors,
      final List<ResponseProcessor> responseProcessors) {
    super(maxSteps,
        buildRequests(requestProcessors),
        buildResponses(responseProcessors));
  }

  private static List<RequestProcessor> buildRequests(final List<RequestProcessor> provided) {
    final List<RequestProcessor> reqs = new ArrayList<>(SingleFlow.REQUEST_PROCESSORS);
    reqs.add(CorrectionProcessor.INSTANCE);
    reqs.add(FinalAnswerRequestProcessor.INSTANCE);
    reqs.addAll(CollectionUtils.nullSafeList(provided));
    reqs.add(LoggingRequestProcessor.INSTANCE);
    return reqs;
  }

  private static List<ResponseProcessor> buildResponses(final List<ResponseProcessor> provided) {
    final List<ResponseProcessor> resps = new ArrayList<>();
    resps.addAll(CollectionUtils.nullSafeList(provided));
    resps.addAll(SingleFlow.RESPONSE_PROCESSORS);
    resps.add(FinalAnswerResponseProcessor.INSTANCE);
    return resps;
  }
}
