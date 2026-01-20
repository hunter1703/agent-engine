package com.agentengine.interfaces.rest.services;

import com.agentengine.engine.client.AgentEngine;
import com.agentengine.engine.client.AgentListener;
import com.agentengine.engine.client.beans.session.Message;
import com.agentengine.engine.client.beans.session.ToolCall;
import com.agentengine.engine.client.beans.session.ToolExecution;
import com.agentengine.engine.client.beans.session.ToolRequest;
import com.agentengine.interfaces.rest.services.beans.ToolPlanEvent;
import com.agui.core.agent.AgentSubscriber;
import com.agui.core.agent.RunAgentInput;
import com.agui.core.exception.AGUIException;
import com.agui.core.message.BaseMessage;
import com.agui.core.state.State;
import com.agui.server.LocalAgent;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class AGUIAgent extends LocalAgent {

    private final AgentEngine agentEngine;
    public AGUIAgent(final String id, final AgentEngine agentEngine) throws AGUIException {
        super(id, new State(), null, "", null);
        this.agentEngine = agentEngine;
    }

    @Override
    protected void run(final RunAgentInput runAgentInput, final AgentSubscriber agentSubscriber) {
        agentEngine.registerListener(runAgentInput.threadId(), new AgentListener() {

            @Override
            public void onToolPlan(String sessionId, Collection<ToolCall> toolCalls) {
                agentSubscriber.onCustomEvent(new ToolPlanEvent(toolCalls));
            }

            @Override
            public void onToolExecution(String sessionId, ToolExecution toolExecution) {
            }

            @Override
            public void onReasoningStart(String sessionId) {
            }

            @Override
            public void onReasoningEnd(String sessionId, Message message) {
            }

            @Override
            public void onToolRepair(String sessionId, List<ToolCall> toolCalls, List<ToolRequest> remainingRequests) {
            }

            @Override
            public void onFinalAnswer(final String sessionId, final Message message) {

            }
        });
    }
}
