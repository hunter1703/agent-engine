package com.agentengine.engine.agents.flows;

import com.agentengine.engine.agents.processors.request.*;
import com.agentengine.engine.agents.processors.response.TurnCompletionResponseProcessor;
import com.agentengine.engine.agents.processors.response.PartOrderingResponseProcessor;
import com.agentengine.engine.agents.processors.response.PlanLoopResponseProcessor;
import com.agentengine.engine.agents.processors.response.RedundantToolCallsResponseProcessor;
import com.agentengine.engine.agents.processors.response.RunCleanupResponseProcessor;
import com.agentengine.engine.agents.processors.response.GuardrailResponseProcessor;
import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.guardrails.Guardrail;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import com.google.adk.models.BaseLlm;
import java.util.ArrayList;
import java.util.List;

public final class DefaultFlow extends AbstractFlow {

  public DefaultFlow(
      final Parser parser,
      final List<Guardrail> outputGuardrails,
      final GuardrailErrorMode guardrailErrorMode) {
    super(Integer.MAX_VALUE, buildRequests(parser), buildResponses(parser, outputGuardrails, guardrailErrorMode));
  }
}
