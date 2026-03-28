package com.agentengine.runtime.session;

import com.agentengine.runtime.actor.StartSessionResult;
import com.agentengine.util.pekko.PekkoSerializable;

public record StartChildResult(String sessionId, StartSessionResult result) implements PekkoSerializable {
}
