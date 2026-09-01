package com.agentengine.agent.core.session.state;

public record CommittedTurn(String id, int count, String lastEventId) {}
