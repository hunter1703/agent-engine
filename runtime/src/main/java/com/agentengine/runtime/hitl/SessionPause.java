package com.agentengine.runtime.hitl;

import com.agentengine.util.common.StringUtils;

public record SessionPause(SessionPauseKind kind, String confirmationId) {

  public SessionPause {
    kind = kind == null ? SessionPauseKind.UNKNOWN : kind;
  }

  public boolean isPaused() {
    return kind != SessionPauseKind.UNKNOWN;
  }

  public boolean hasConfirmationId() {
    return StringUtils.isNotBlank(confirmationId);
  }
}
