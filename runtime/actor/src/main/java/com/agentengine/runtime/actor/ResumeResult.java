package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

/** Reply to a session resume request. */
public interface ResumeResult extends PekkoSerializable {
  record Resumed() implements ResumeResult {
  }
  record Rejected(String reason) implements ResumeResult {
  }
}
