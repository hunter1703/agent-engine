package com.agentengine.runtime.session.events;

import com.agentengine.runtime.session.state.StartingChild;
import com.agentengine.util.common.beans.UniqueRecord;

public final class ChildStartingFact extends SessionFact {
    private StartingChild child;

    public ChildStartingFact(){}

    public ChildStartingFact(final StartingChild child) {
        this.child = child;
    }

    public StartingChild getChild() {
        return child;
    }

    public void setChild(final StartingChild child) {
        this.child = child;
    }
}
