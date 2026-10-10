package com.agentengine.agent.core.session.state;

import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.common.beans.UniqueRecord;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

public record RunState(
    String runId,
    UniqueRecord<EnqueuedMessage> message,
    long messagePickedTimestamp,
    ZoneId timeZone,
    int startSequence,
    CommittedTurn lastCommittedTurn,
    Set<Integer> committedTurnIds,
    RunResult result,
    boolean settled) {

  /** The run of the parent session that sent this run's message, or null if no parent did. */
  public String parentRunId() {
    return message == null ? null : message.getRecord().parentRunId();
  }

  /** When the run's message was picked, in the zone of the user who sent it. */
  public ZonedDateTime sentAt() {
    return ZonedDateTime.ofInstant(Instant.ofEpochMilli(messagePickedTimestamp), timeZone);
  }

  public RunState withCommittedTurn(final CommittedTurn turn) {
    final Set<Integer> updatedCommittedTurnIds = new HashSet<>(committedTurnIds);
    updatedCommittedTurnIds.add(turn.turnId());
    return new RunState(
        runId,
        message,
        messagePickedTimestamp,
        timeZone,
        startSequence,
        turn,
        updatedCommittedTurnIds,
        result,
        settled);
  }

  public RunState complete(final RunResult result) {
    return new RunState(
        runId,
        message,
        messagePickedTimestamp,
        timeZone,
        startSequence,
        lastCommittedTurn,
        committedTurnIds,
        result,
        false);
  }

  /**
   * Marks that every action {@code afterComplete} owns for this run's completion (status update,
   * terminal/error publish, parent/queue notification) has actually run to completion — not merely
   * that {@link #complete} was applied. Recovery uses this distinction: a run whose completion was
   * durably persisted but crashed before this flag was ever set has genuinely unfinished
   * post-completion work, while one where it's already {@code true} needs nothing redone.
   */
  public RunState withSettled() {
    return new RunState(
        runId,
        message,
        messagePickedTimestamp,
        timeZone,
        startSequence,
        lastCommittedTurn,
        committedTurnIds,
        result,
        true);
  }

  public RunState finished() {
    return new RunState(
        runId, null, -1L, timeZone, startSequence, lastCommittedTurn, Set.of(), null, settled);
  }
}
