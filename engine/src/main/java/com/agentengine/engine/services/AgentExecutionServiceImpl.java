package com.agentengine.engine.services;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.agents.AgentSessionRuntime;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@Singleton
public class AgentExecutionServiceImpl implements AgentExecutionService {

  private final AgentSessionRuntimeManager agentSessionRuntimeManager;
  private final AgentRunner agentRunner;

  @Inject
  public AgentExecutionServiceImpl(AgentSessionRuntimeManager agentSessionRuntimeManager, AgentRunner agentRunner) {
    this.agentSessionRuntimeManager = agentSessionRuntimeManager;
    this.agentRunner = agentRunner;
  }

  @Override
  @WithSpan
  @Retry(maxRetries = 2)
  @Timeout(5000)
  public Flowable<Event> run(AgentRequest request) {
    AgentSessionRuntime runtime = agentSessionRuntimeManager.getOrStartRuntime(request.getAgentId(),
        request.getSessionId());
    return agentRunner.run(runtime, request.getMessage());
  }
}
