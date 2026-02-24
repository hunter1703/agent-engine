package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleFlow extends AbstractFlow {

  public SimpleFlow(
      final int maxSteps,
      final List<RequestProcessor> requestProcessors,
      final List<ResponseProcessor> responseProcessors) {
    super(maxSteps, CollectionUtils.append(SingleFlow.REQUEST_PROCESSORS, requestProcessors, LoggingRequestProcessor.INSTANCE), CollectionUtils.append(responseProcessors, RESPONSE_PROCESSORS));
  }
}
