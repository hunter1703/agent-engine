package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

/** Reply to a session start request. */
public interface StartSessionResult extends PekkoSerializable {
    record Accepted() implements StartSessionResult {}
    record DuplicateRequest() implements StartSessionResult {}

    record Queued(int position) implements StartSessionResult {}

    record Rejected(String reason) implements StartSessionResult {}
}
