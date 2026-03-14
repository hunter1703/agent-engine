package com.agentengine.engine.api.services;

import com.agentengine.util.ms.MicroService;
import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;

@MicroService("agent")
public interface AgentExecutionService {

  Flowable<BaseEvent> run(String agentId, String sessionId, String text);

  Flowable<BaseEvent> resumeSession(String agentId, String sessionId, Boolean confirmed, String answer);
}
