package com.agentengine.agent.infra.agents;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks;
import java.util.Collections;
import java.util.List;

/** Engine base agent — carries {@link BaseAgentConfig} and delegates runtime to subclasses. */
public abstract class Agent extends BaseAgent {
    private final BaseAgentConfig agentConfig;

    protected Agent(Builder<?, ?> builder) {
        this(
                builder.name(),
                builder.description(),
                builder.subAgents(),
                builder.agentConfig(),
                builder.beforeAgentCallback(),
                builder.afterAgentCallback());
    }

    protected Agent(
            String name,
            String description,
            List<? extends BaseAgent> subAgents,
            BaseAgentConfig agentConfig,
            List<? extends Callbacks.BeforeAgentCallback> beforeAgentCallbacks,
            List<? extends Callbacks.AfterAgentCallback> afterAgentCallbacks) {
        super(name, description, subAgents, beforeAgentCallbacks, afterAgentCallbacks);
        this.agentConfig = agentConfig;
    }

    public BaseAgentConfig getAgentConfig() {
        return agentConfig;
    }

    public abstract static class Builder<B extends Builder<?, ?>, A extends Agent> {
        private BaseAgentConfig agentConfig;
        private List<? extends Agent> subAgents = List.of();

        public String name() {
            return agentConfig.getId();
        }

        public String description() {
            final String description = agentConfig.getDescription();
            return StringUtils.isNotBlank(description) ? description : "Agent with id: " + agentConfig.getId();
        }

        public B subAgents(List<? extends Agent> subAgents) {
            this.subAgents = subAgents;
            // noinspection unchecked
            return (B) this;
        }

        public List<? extends Agent> subAgents() {
            return subAgents;
        }

        public B agentConfig(BaseAgentConfig agentConfig) {
            this.agentConfig = agentConfig;
            // noinspection unchecked
            return (B) this;
        }

        public BaseAgentConfig agentConfig() {
            return agentConfig;
        }

        public B beforeAgentCallback(List<? extends Callbacks.BeforeAgentCallback> beforeAgentCallback) {
            // noinspection unchecked
            return (B) this;
        }

        public List<? extends Callbacks.BeforeAgentCallback> beforeAgentCallback() {
            return Collections.emptyList();
        }

        public B afterAgentCallback(List<? extends Callbacks.AfterAgentCallback> afterAgentCallback) {
            // noinspection unchecked
            return (B) this;
        }

        public List<? extends Callbacks.AfterAgentCallback> afterAgentCallback() {
            return Collections.emptyList();
        }

        public abstract A build();
    }
}
