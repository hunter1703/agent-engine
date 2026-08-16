package com.agentengine.agent.core.session;

import com.agentengine.util.pekko.PekkoSerializable;

public record StartChildResult(String sessionId, StartSessionResult result)
    implements PekkoSerializable {}
