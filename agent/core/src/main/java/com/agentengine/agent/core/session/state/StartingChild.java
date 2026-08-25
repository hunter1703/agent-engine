package com.agentengine.agent.core.session.state;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.util.common.beans.UniqueRecord;

public record StartingChild(String agentId, String sessionId, UniqueRecord<UserMessage> message) {}
