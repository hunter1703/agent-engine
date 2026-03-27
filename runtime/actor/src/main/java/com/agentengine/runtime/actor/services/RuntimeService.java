package com.agentengine.runtime.actor.services;

import com.agentengine.runtime.actor.SessionReply;
import com.agentengine.util.ms.MicroService;

import java.util.concurrent.CompletionStage;

@MicroService("runtime")
public interface RuntimeService {

    CompletionStage<SessionReply.StartRunResult> startSession(String agentId, String sessionId, String message);

    CompletionStage<SessionReply.ResumeResult> resumeSession(String agentId, String sessionId,
                                                              String confirmationId, boolean confirmed, String answer);
}
