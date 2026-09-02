package com.agentengine.agent.core.session.state;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.common.beans.UniqueRecord;

public record RunState(
    String runId,
    UniqueRecord<UserMessage> message,
    long messageTimestamp,
    long startSequence,
    CommittedTurn lastCommittedTurn,
    RunResult result) {

  public RunState withCommittedTurn(final CommittedTurn turn) {
    return new RunState(runId, message, messageTimestamp, startSequence, turn, result);
  }

  public RunState complete(final RunResult result) {
    return new RunState(runId, message, messageTimestamp, startSequence, lastCommittedTurn, result);
  }

  public RunState finished() {
    return new RunState(runId, null, -1L, startSequence, lastCommittedTurn, null);
  }
}
