package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.flows.llmflows.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimpleFlow extends SingleFlow {
  private static final List<RequestProcessor> DEFAULT_PROCESSORS = new ArrayList<>();
  static {
    DEFAULT_PROCESSORS.addAll(SingleFlow.REQUEST_PROCESSORS);
    DEFAULT_PROCESSORS.add(new PlanContextRequestProcessor());
  }

  public SimpleFlow(int maxSteps, List<RequestProcessor> requestProcessors,
      List<ResponseProcessor> responseProcessors) {
    super(CollectionUtils.append(DEFAULT_PROCESSORS, requestProcessors), CollectionUtils.append(responseProcessors, RESPONSE_PROCESSORS), Optional.of(maxSteps));
  }
}
