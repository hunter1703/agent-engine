package com.agentengine.runtime.api.services;

import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.ms.MicroService;
import org.reactivestreams.Publisher;

@MicroService("runtime")
public interface RuntimeService {

    Publisher<SessionEvent> startSession(String agentId, String sessionId, String message);

    Publisher<SessionEvent> confirmSession(String sessionId, Confirmation confirmation);
}
