package com.agentengine.runtime.session.state;

import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.util.pekko.PekkoSerializable;

public record ChildSession(String agentId, RunResult lastResult) implements PekkoSerializable {}
