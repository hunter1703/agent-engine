package com.agentengine.agent.infra.compaction;

import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import io.reactivex.rxjava3.core.Maybe;

/** Compacts the history of a model request that is about to be sent. */
public interface EventCompactor {

  /**
   * The compaction event the history needs before {@code request}, the request being built for the
   * model call, gets its history; empty when it needs none, or the run made no model call yet.
   */
  Maybe<Event> compact(InvocationContext context, LlmRequest request);
}
