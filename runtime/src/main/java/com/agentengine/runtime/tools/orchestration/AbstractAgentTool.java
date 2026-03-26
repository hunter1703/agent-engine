package com.agentengine.runtime.tools.orchestration;

import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.actor.SessionCommand;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class AbstractAgentTool extends Tool {
  private final SessionActorFactory actorFactory;

  protected AbstractAgentTool(final ToolDescriptor toolDescriptor, final SessionActorFactory actorFactory) {
    this(toolDescriptor, actorFactory, false);
  }

  protected AbstractAgentTool(final ToolDescriptor toolDescriptor, final SessionActorFactory actorFactory, final boolean longRunning) {
    super(toolDescriptor, longRunning);
    this.actorFactory = actorFactory;
  }

  protected EntityRef<SessionCommand> actorRef(final ToolContext toolContext) {
    return actorFactory.entityRef(ToolUtils.agentId(toolContext), ToolUtils.sessionId(toolContext));
  }
}
