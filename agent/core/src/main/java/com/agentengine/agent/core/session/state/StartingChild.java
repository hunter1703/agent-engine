package com.agentengine.agent.core.session.state;

import com.agentengine.util.common.beans.UniqueRecord;

public record StartingChild(String agentId, String sessionId, UniqueRecord<String> message) {}
