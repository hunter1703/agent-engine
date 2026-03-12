package com.agentengine.engine.api.services;

import com.agentengine.engine.api.beans.ConfirmationDecision;
import com.agentengine.util.ms.MicroService;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

@MicroService("agent")
public interface AgentExecutionService {

  Flowable<Event> run(String agentId, String sessionId, String text);

  Flowable<Event> resumeSession(String agentId, String sessionId, ConfirmationDecision decision, String answer);
}
