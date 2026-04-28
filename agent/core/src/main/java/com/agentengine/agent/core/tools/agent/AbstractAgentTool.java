package com.agentengine.agent.core.tools.agent;

import com.agentengine.agent.core.session.SessionActor;
import com.agentengine.agent.core.session.commands.SessionCommand;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.utils.ToolUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.google.adk.tools.ToolContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class AbstractAgentTool extends Tool {

    protected final ActorSystemProvider actorSystemProvider;

    protected AbstractAgentTool(final ToolDescriptor toolDescriptor, final ActorSystemProvider actorSystemProvider) {
        this(toolDescriptor, actorSystemProvider, false);
    }

    protected AbstractAgentTool(
            final ToolDescriptor toolDescriptor,
            final ActorSystemProvider actorSystemProvider,
            final boolean isLongRunning) {
        super(toolDescriptor, isLongRunning);
        this.actorSystemProvider = actorSystemProvider;
    }

    protected EntityRef<SessionCommand> actorRef(final ToolContext toolContext) {
        return actorSystemProvider.entityRefFor(SessionActor.TYPE_KEY, ToolUtils.sessionId(toolContext));
    }
}
