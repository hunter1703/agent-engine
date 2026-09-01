package com.agentengine.agent.core.session.state;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.common.beans.UniqueRecord;

public record RunState(
    String runId,
    UniqueRecord<UserMessage> message,
    long messageTimestamp,
    CommittedTurn lastCommittedTurn,
    RunResult result) {

  public RunState withCommittedTurn(final CommittedTurn committedTurn) {
    return new RunState(runId, message, messageTimestamp, committedTurn, result);
  }

  public RunState resetMessage() {
    return new RunState(runId, null, -1L, lastCommittedTurn, result);
  }

  public RunState withResult(final RunResult result) {
    return new RunState(runId, message, messageTimestamp, lastCommittedTurn, result);
  }
}
