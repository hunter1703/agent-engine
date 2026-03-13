package com.agentengine.engine.hitl;

import com.agentengine.util.common.StringUtils;
import java.util.List;

public record SessionPause(SessionPauseKind kind, String prompt, List<String> options, String confirmationId) {

  public SessionPause {
    kind = kind == null ? SessionPauseKind.UNKNOWN : kind;
    options = options == null ? List.of() : List.copyOf(options);
  }

  public boolean isPaused() {
    return kind != SessionPauseKind.UNKNOWN;
  }

  public boolean hasConfirmationId() {
    return StringUtils.isNotBlank(confirmationId);
  }
}
