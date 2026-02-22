package com.agentengine.engine.api.services;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.ms.MicroService;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

@MicroService
public interface AgentExecutionService {

  Flowable<Event> run(AgentRequest request);
}
