package com.agentengine.engine.api.services;

import com.agentengine.engine.api.AgentRequest;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

import com.agentengine.engine.api.MicroService;

@MicroService
public interface AgentExecutionService {

  Flowable<Event> run(AgentRequest request);
}
