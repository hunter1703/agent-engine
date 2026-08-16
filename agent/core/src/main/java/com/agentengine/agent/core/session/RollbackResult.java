package com.agentengine.agent.core.session;

import com.agentengine.util.pekko.PekkoSerializable;

/** Reply to a session rollback request. */
public interface RollbackResult extends PekkoSerializable {
  record Applied() implements RollbackResult {}

  record Rejected(String reason) implements RollbackResult {}
}
