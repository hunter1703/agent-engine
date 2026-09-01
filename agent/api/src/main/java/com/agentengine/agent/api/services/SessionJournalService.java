package com.agentengine.agent.api.services;

import com.agentengine.util.ms.client.MicroService;
import java.util.List;

@MicroService("agent")
public interface SessionJournalService {
  List<String> getCommittedTurnIds(String sessionId);
}
