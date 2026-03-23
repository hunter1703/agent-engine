package com.agentengine.runtime.actor.services;

import com.agentengine.runtime.actor.SessionActor;
import com.agentengine.util.ms.MicroService;

import java.util.concurrent.CompletionStage;

@MicroService("runtime")
public interface RuntimeService {

    CompletionStage<SessionActor.RunReceipt> startSession(String agentId, String sessionId, String message);

    CompletionStage<SessionActor.RunReceipt> resumeSession(String agentId, String sessionId,
                                                            String confirmationId, boolean confirmed, String answer);
}
