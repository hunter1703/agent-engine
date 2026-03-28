package com.agentengine.runtime.factories;

import com.agentengine.core.api.services.AgentService;
import com.agentengine.runtime.session.commands.SessionCommand;
import com.agentengine.runtime.session.SessionRunner;
import com.agentengine.runtime.agents.Agent;
import com.agentengine.runtime.context.ContextManager;
import com.agentengine.runtime.factories.agent.AgentProvider;
import com.agentengine.runtime.factories.context.ContextManagerProvider;
import com.agentengine.runtime.guardrails.GuardrailPolicyFactory;
import com.agentengine.runtime.plugins.ContextManagementPlugin;
import com.agentengine.runtime.plugins.GuardrailPlugin;
import com.agentengine.runtime.plugins.PluginGroup;
import com.agentengine.runtime.services.MongoSessionService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.BaseAgent;
import com.google.adk.apps.App;
import com.google.adk.apps.ResumabilityConfig;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.memory.InMemoryMemoryService;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.runner.Runner;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.*;

@Singleton
public class RunnerFactory {

  private final AgentService agentService;
  private final AgentProvider agentProvider;
  private final ContextManagerProvider contextManagerProvider;
  private final GuardrailPolicyFactory guardrailPolicyFactory;
  private final MongoSessionService sessionService;

  public RunnerFactory(AgentService agentService, AgentProvider agentProvider, ContextManagerProvider contextManagerProvider,
      GuardrailPolicyFactory guardrailPolicyFactory, MongoSessionService sessionService) {
    this.agentService = agentService;
    this.agentProvider = agentProvider;
    this.contextManagerProvider = contextManagerProvider;
    this.guardrailPolicyFactory = guardrailPolicyFactory;
    this.sessionService = sessionService;
  }

  public SessionRunner buildRunner(final String agentId, final String sessionId, final ActorRef<SessionCommand> actor) {
    final BaseAgentConfig config = agentService.getAgent(agentId);
    final Agent agent = agentProvider.create(config);
    final App app = App.builder().plugins(buildPlugins(agent)).rootAgent(agent).name(agentId)
        .resumabilityConfig(new ResumabilityConfig(true)).build();
    final Runner runner = Runner.builder().app(app).artifactService(new InMemoryArtifactService()).sessionService(sessionService)
        .memoryService(new InMemoryMemoryService()).build();
    return new SessionRunner(sessionId, actor, runner);
  }

  private List<BasePlugin> buildPlugins(final Agent rootAgent) {
    final Queue<Agent> queue = new ArrayDeque<>();
    queue.add(rootAgent);
    final Set<String> visited = new HashSet<>();
    final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policies = new LinkedHashMap<>();
    final Map<String, ContextManager> contextManagers = new LinkedHashMap<>();

    while (!queue.isEmpty()) {
      final Agent agent = queue.poll();
      if (!visited.add(agent.name())) {
        continue;
      }
      contextManagers.put(agent.name(), contextManagerProvider.create(agent.getAgentConfig()));
      final GuardrailPolicyFactory.GuardrailPolicy policy = guardrailPolicyFactory.build(agent.getAgentConfig().getGuardrails());
      if (policy.enabled()) {
        policies.put(agent.name(), policy);
      }
      for (final BaseAgent subAgent : CollectionUtils.nullSafeList(agent.subAgents())) {
        if (subAgent instanceof Agent nestedAgent) {
          queue.add(nestedAgent);
        }
      }
    }

    return List.of(new PluginGroup("engine", List.of(new GuardrailPlugin(policies), new ContextManagementPlugin(contextManagers))));
  }
}
