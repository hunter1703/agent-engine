package com.agentengine.agent.core.session.state;

import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.pekko.PekkoSerializable;

public record ChildSession(String agentId, RunResult lastResult) implements PekkoSerializable {}
