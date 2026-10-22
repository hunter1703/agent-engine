package com.agentengine.agent.infra.agents;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import java.util.function.Supplier;

/** An ADK agent built from an engine agent config. */
public interface ConfiguredAgent {

  BaseAgentConfig getAgentConfig();

  /**
   * Runs this agent with its own context added to the turn's, so whatever runs as part of it — its
   * tools, and the plugin callbacks around it — also reaches what was shared with this agent. For
   * the session's own agent that context is already the turn's; an agent transferred to inside the
   * session keeps everything the session reaches and adds its own.
   */
  default Flowable<Event> actingAsThisAgent(final Supplier<Flowable<Event>> run) {
    return Flowable.fromPublisher(
        subscriber ->
            Context.require()
                .alsoActingAs(
                    Principal.ofAnyUser().within(AssetClass.AGENT, getAgentConfig().getId()))
                .run(() -> run.get().subscribe(subscriber)));
  }
}
