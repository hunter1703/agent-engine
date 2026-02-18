package com.agentengine.engine.api.services;

import com.agentengine.engine.api.AgentRequest;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

public interface AgentExecutionService {

    Flowable<Event> run(AgentRequest request);

    Flowable<Event> runStreaming(AgentRequest request);
}
