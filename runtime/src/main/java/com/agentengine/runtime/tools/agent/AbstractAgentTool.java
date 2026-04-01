package com.agentengine.runtime.tools.agent;

import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class AbstractAgentTool extends Tool {

    protected AbstractAgentTool(final ToolDescriptor toolDescriptor, final SessionActorFactory actorFactory) {
        super(toolDescriptor, actorFactory);
    }

    protected EntityRef<SessionCommand> actorRef(final ToolContext toolContext) {
        return sessionActorFactory.entityRef(ToolUtils.agentId(toolContext), ToolUtils.sessionId(toolContext));
    }
}
