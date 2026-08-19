package com.agentengine.agent.core.session.state;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.core.session.events.RunResult;
import com.agentengine.util.common.beans.UniqueRecord;
import com.google.adk.events.Event;
import java.util.List;

public record RunState(
    String runId,
    UniqueRecord<UserMessage> message,
    long messageTimestamp,
    List<Event> lastCommittedTurn,
    RunResult result) {

  public RunState withEvents(final List<Event> events) {
    return new RunState(runId, message, messageTimestamp, events, result);
  }

  public RunState resetMessage() {
    return new RunState(runId, null, -1L, lastCommittedTurn, result);
  }

  public RunState withResult(final RunResult result) {
    return new RunState(runId, message, messageTimestamp, lastCommittedTurn, result);
  }
}
