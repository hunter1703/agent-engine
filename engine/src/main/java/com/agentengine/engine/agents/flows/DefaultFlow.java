package com.agentengine.engine.agents.flows;

import com.agentengine.engine.agents.processors.request.*;
import com.agentengine.engine.agents.processors.response.TurnCompletionResponseProcessor;
import com.agentengine.engine.agents.processors.response.PartOrderingResponseProcessor;
import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.agents.processors.response.RedundantToolCallsResponseProcessor;
import com.agentengine.engine.agents.processors.response.RunCleanupResponseProcessor;
import com.agentengine.engine.agents.processors.response.GuardrailResponseProcessor;
import com.agentengine.engine.agents.processors.response.OutputEvaluationResponseProcessor;
import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.guardrails.Guardrail;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import java.util.ArrayList;
import java.util.List;

public class DefaultFlow extends AbstractFlow {

  public DefaultFlow(final Parser parser) {
    this(parser, List.of(), GuardrailErrorMode.FAIL_OPEN);
  }

  public DefaultFlow(
      final Parser parser,
      final List<Guardrail> outputGuardrails,
      final GuardrailErrorMode guardrailErrorMode) {
    super(
        Integer.MAX_VALUE,
        buildRequests(parser),
        buildResponses(parser, outputGuardrails, guardrailErrorMode));
  }

  private static List<RequestProcessor> buildRequests(final Parser parser) {
    final List<RequestProcessor> requestProcessors = new ArrayList<>();
    requestProcessors.add(RunInitRequestProcessor.INSTANCE);
    requestProcessors.add(HumanInTheLoopRequestProcessor.INSTANCE);
    requestProcessors.addAll(SingleFlow.REQUEST_PROCESSORS);
    requestProcessors.add(CorrectionProcessor.INSTANCE);
    requestProcessors.add(PlanningRequestProcessor.INSTANCE);
    requestProcessors.add(parser);
    requestProcessors.add(LoggingRequestProcessor.INSTANCE);
    return requestProcessors;
  }

  private static List<ResponseProcessor> buildResponses(
      final Parser parser,
      final List<Guardrail> outputGuardrails,
      final GuardrailErrorMode guardrailErrorMode) {
    final List<ResponseProcessor> responseProcessors = new ArrayList<>();
    responseProcessors.add(parser);
    responseProcessors.add(new GuardrailResponseProcessor(outputGuardrails, guardrailErrorMode));
    responseProcessors.add(PlanLoopResponseProcessor.INSTANCE);
    responseProcessors.add(OutputEvaluationResponseProcessor.INSTANCE);
    responseProcessors.add(RedundantToolCallsResponseProcessor.INSTANCE);
    responseProcessors.add(TurnCompletionResponseProcessor.INSTANCE);
    responseProcessors.addAll(SingleFlow.RESPONSE_PROCESSORS);
    responseProcessors.add(PartOrderingResponseProcessor.INSTANCE);
    responseProcessors.add(RunCleanupResponseProcessor.INSTANCE);
    return responseProcessors;
  }
}
