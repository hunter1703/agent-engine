package com.agentengine.agent.core.session.events;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.pekko.PekkoSerializable;

public record RunResult(String output, String failureMessage, boolean completedRun)
    implements PekkoSerializable {

  public static RunResult success(final String output) {
    return new RunResult(output, null, true);
  }

  public static RunResult failure(final String failureMessage) {
    return new RunResult(null, failureMessage, true);
  }

  public static RunResult incomplete() {
    return new RunResult(null, null, false);
  }

  public boolean isFailure() {
    return StringUtils.isNotBlank(failureMessage);
  }
}
