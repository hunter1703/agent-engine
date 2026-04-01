package com.agentengine.runtime.actor;

import com.agentengine.util.agents.beans.Confirmation;
import com.agentengine.util.ms.MicroService;
import java.util.concurrent.CompletionStage;

@MicroService("runtime")
public interface RuntimeService {

    CompletionStage<StartSessionResult> startSession(String agentId, String sessionId, String message);

    CompletionStage<ConfirmResult> confirmSession(String agentId, String sessionId, Confirmation confirmation);
}
