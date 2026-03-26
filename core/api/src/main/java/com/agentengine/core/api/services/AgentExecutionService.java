package com.agentengine.core.api.services;

import com.agentengine.util.ms.MicroService;
import com.agui.core.event.BaseEvent;
import org.reactivestreams.Publisher;

@MicroService("agent")
public interface AgentExecutionService {

  Publisher<BaseEvent> run(String agentId, String text);

  Publisher<BaseEvent> resumeSession(String sessionId, String confirmationId, Boolean confirmed, String answer);
}
