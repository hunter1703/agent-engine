package com.agentengine.runtime.actor;

import com.agentengine.util.ms.MicroService;

@MicroService("runtime")
public interface RuntimeService {

  StartSessionResult startSession(String agentId, String sessionId, String message);

  ResumeResult resumeSession(String agentId, String sessionId, String confirmationId, boolean confirmed, String answer);
}
