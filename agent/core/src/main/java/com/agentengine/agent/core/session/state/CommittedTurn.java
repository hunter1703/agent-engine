package com.agentengine.agent.core.session.state;

public record CommittedTurn(int turnId, int startSequence, int count, String lastEventId) {}
