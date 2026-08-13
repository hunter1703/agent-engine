package com.agentengine.agent.core.session;

import com.agentengine.util.pekko.PekkoSerializable;

/** Reply to a session resume request. */
public interface ResumeResult extends PekkoSerializable {
    record Accepted() implements ResumeResult {}

    record Rejected(String reason) implements ResumeResult {}

    record UnknownInterruptId() implements ResumeResult {}
}
