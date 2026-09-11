package com.agentengine.agent.core.session.state;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.common.beans.UniqueRecord;
import java.util.HashSet;
import java.util.Set;

public record RunState(
    String runId,
    UniqueRecord<UserMessage> message,
    long messagePickedTimestamp,
    int startSequence,
    CommittedTurn lastCommittedTurn,
    Set<Integer> committedTurnIds,
    RunResult result) {

  public RunState withCommittedTurn(final CommittedTurn turn) {
    final Set<Integer> updatedCommittedTurnIds = new HashSet<>(committedTurnIds);
    updatedCommittedTurnIds.add(turn.turnId());
    return new RunState(
        runId,
        message,
        messagePickedTimestamp,
        startSequence,
        turn,
        updatedCommittedTurnIds,
        result);
  }

  public RunState complete(final RunResult result) {
    return new RunState(
        runId,
        message,
        messagePickedTimestamp,
        startSequence,
        lastCommittedTurn,
        committedTurnIds,
        result);
  }

  public RunState finished() {
    return new RunState(runId, null, -1L, startSequence, null, Set.of(), null);
  }
}
