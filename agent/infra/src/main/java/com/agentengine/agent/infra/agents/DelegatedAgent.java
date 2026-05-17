package com.agentengine.agent.infra.agents;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;

/**
 * Engine wrapper over ADK {@link BaseAgent} with config and optional delegation support.
 *
 * <p>Runtime behavior is forwarded to the delegated agent. Subclasses may override {@link
 * #runAsyncImpl} and {@link #runLiveImpl} directly instead.
 */
public class DelegatedAgent extends Agent {
    private final BaseAgent delegated;

    public DelegatedAgent(final BaseAgent delegated, final BaseAgentConfig agentConfig) {
        super(
                delegated.name(),
                delegated.description(),
                delegated.subAgents(),
                agentConfig,
                delegated.beforeAgentCallback(),
                delegated.afterAgentCallback());
        this.delegated = delegated;
    }

    @Override
    public List<? extends BaseAgent> subAgents() {
        return delegated != null ? delegated.subAgents() : super.subAgents();
    }

    @Override
    public Completable close() {
        return delegated.close();
    }

    @Override
    protected Flowable<Event> runAsyncImpl(final InvocationContext invocationContext) {
        return delegated.runAsync(invocationContext);
    }

    @Override
    protected Flowable<Event> runLiveImpl(final InvocationContext invocationContext) {
        return delegated.runLive(invocationContext);
    }
}
