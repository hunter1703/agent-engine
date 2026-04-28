package com.agentengine.agent.core.session;

import com.agentengine.util.pekko.PekkoSerializable;

/** Reply to a session confirmation request. */
public interface ConfirmResult extends PekkoSerializable {
    record Accepted() implements ConfirmResult {}

    record Rejected(String reason) implements ConfirmResult {}

    record UnknownConfirmationId() implements ConfirmResult {}
}
