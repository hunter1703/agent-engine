package com.agentengine.agent.core.session.state;

public record CommittedTurn(String turnId, long startSequence, int count, String lastEventId) {}
