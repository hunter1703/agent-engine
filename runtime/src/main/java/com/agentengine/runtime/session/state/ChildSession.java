package com.agentengine.runtime.session.state;

import com.agentengine.runtime.session.events.RunResult;
import com.agentengine.util.pekko.PekkoSerializable;

public record ChildSession(String agentId, RunResult lastResult) implements PekkoSerializable {

    public ChildSession startRun() {
        return new ChildSession(agentId, null);
    }

    public ChildSession complete(final RunResult result) {
        return new ChildSession(agentId, result);
    }
}
