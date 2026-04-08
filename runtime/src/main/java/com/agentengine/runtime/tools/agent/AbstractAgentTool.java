package com.agentengine.runtime.tools.agent;

import com.agentengine.runtime.session.SessionActor;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.runtime.utils.ToolUtils;
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
