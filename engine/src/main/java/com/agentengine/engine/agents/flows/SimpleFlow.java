package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.flows.llmflows.*;

import java.util.List;
import java.util.Optional;

public class SimpleFlow extends SingleFlow {

  public SimpleFlow(int maxSteps, List<RequestProcessor> requestProcessors,
      List<ResponseProcessor> responseProcessors) {
    super(CollectionUtils.append(REQUEST_PROCESSORS, requestProcessors),
        CollectionUtils.append(responseProcessors, RESPONSE_PROCESSORS), Optional.of(maxSteps));
  }
}
