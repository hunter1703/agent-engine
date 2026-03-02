package com.agentengine.engine.agents.flows;

import com.agentengine.engine.agents.processors.request.*;
import com.agentengine.engine.agents.processors.response.TurnCompletionResponseProcessor;
import com.agentengine.engine.agents.processors.response.FinalAnswerResponseProcessor;
import com.agentengine.engine.agents.processors.response.PartOrderingResponseProcessor;
import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.agents.processors.response.RedundantToolCallsResponseProcessor;
import com.agentengine.engine.agents.processors.response.RunCleanupResponseProcessor;
import com.agentengine.engine.agents.processors.Parser;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import java.util.ArrayList;
import java.util.List;

public class DefaultFlow extends AbstractFlow {

  public DefaultFlow(final Parser parser) {
    super(Integer.MAX_VALUE, buildRequests(parser), buildResponses(parser));
  }

  private static List<RequestProcessor> buildRequests(final Parser parser) {
      final List<RequestProcessor> requestProcessors = new ArrayList<>();
      requestProcessors.add(RunInitRequestProcessor.INSTANCE);
    requestProcessors.addAll(SingleFlow.REQUEST_PROCESSORS);
    requestProcessors.add(CorrectionProcessor.INSTANCE);
    requestProcessors.add(PlanningRequestProcessor.INSTANCE);
    requestProcessors.add(FinalAnswerRequestProcessor.INSTANCE);
    requestProcessors.add(parser);
    requestProcessors.add(LoggingRequestProcessor.INSTANCE);
    return requestProcessors;
  }

  private static List<ResponseProcessor> buildResponses(final Parser parser) {
    final List<ResponseProcessor> responseProcessors = new ArrayList<>();
    responseProcessors.add(parser);
    responseProcessors.add(PlanLoopResponseProcessor.INSTANCE);
    responseProcessors.add(RedundantToolCallsResponseProcessor.INSTANCE);
    responseProcessors.add(FinalAnswerResponseProcessor.INSTANCE);
    responseProcessors.add(TurnCompletionResponseProcessor.INSTANCE);
    responseProcessors.addAll(SingleFlow.RESPONSE_PROCESSORS);
    responseProcessors.add(PartOrderingResponseProcessor.INSTANCE);
    responseProcessors.add(RunCleanupResponseProcessor.INSTANCE);
    return responseProcessors;
  }

  public List<ResponseProcessor> getResponseProcessors() {
    return super.responseProcessors;
  }
}
