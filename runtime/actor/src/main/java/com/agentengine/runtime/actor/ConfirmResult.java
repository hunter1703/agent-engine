package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

/** Reply to a session resume request. */
public interface ConfirmResult extends PekkoSerializable {
    record Accepted() implements ConfirmResult {}

    record Rejected(String reason) implements ConfirmResult {}
    record UnknownConfirmationId() implements ConfirmResult {}
}
