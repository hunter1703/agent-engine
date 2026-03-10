package com.agentengine.engine.services;

import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_USER_ID;
import static com.agentengine.engine.utils.SessionUtils.buildInitialState;
import static com.google.adk.agents.RunConfig.StreamingMode.SSE;
import static com.google.genai.types.Part.fromText;

import com.agentengine.engine.agents.AgentSessionRuntime;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.agents.SessionTitleGenerator;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.CompactionContextStrategyConfig;
import com.agentengine.engine.api.beans.config.ContextStrategyConfig;
import com.agentengine.engine.api.exception.AssetNotFoundException;
import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.agentengine.engine.api.services.AgentService;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.api.utils.AgentUtils;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.builders.agent.AgentProvider;
import com.agentengine.engine.builders.context.ContextManagerProvider;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.events.SessionDeletedEvent;
import com.agentengine.engine.guardrails.GuardrailPlugin;
import com.agentengine.engine.guardrails.GuardrailPolicyFactory;
import com.agentengine.engine.plugins.AgentRunLifecyclePlugin;
import com.agentengine.engine.plugins.ContextManagementPlugin;
import com.agentengine.engine.plugins.HumanInTheLoopPlugin;
import com.agentengine.engine.plugins.ModelInvocationPipeline;
import com.agentengine.engine.plugins.PluginGroup;
import com.agentengine.engine.infra.DefaultModelConfig;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.agentengine.engine.utils.RefCountedCache;
import com.agentengine.engine.validation.ConfigValidationService;
import com.agentengine.engine.api.ContextManager;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.apps.App;
import com.google.adk.apps.ResumabilityConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.adk.summarizer.BaseEventSummarizer;
import com.google.adk.summarizer.EventsCompactionConfig;
import com.google.adk.summarizer.LlmEventSummarizer;
import com.google.genai.types.Content;
import io.quarkus.arc.Unremovable;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class AgentExecutionServiceImpl implements AgentExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionServiceImpl.class);
    private static final long DEFAULT_IDLE_TIMEOUT_MINUTES = 30L;

    private final AgentService agentService;
    private final AgentProvider agentProvider;
    private final AgentSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final GuardrailPolicyFactory guardrailPolicyFactory;
    private final SessionTitleGenerator sessionTitleGenerator;
    private final ContextManagerProvider contextManagerProvider;

    private RefCountedCache<String, AgentSessionRuntime> runtimeCache;

    @Inject
    public AgentExecutionServiceImpl(
            final AgentService agentService,
            final AgentProvider agentProvider,
            final AgentSessionRepository sessionRepository,
            final SessionService sessionService,
            final GuardrailPolicyFactory guardrailPolicyFactory,
            final SessionTitleGenerator sessionTitleGenerator,
            final ContextManagerProvider contextManagerProvider) {
        this.agentService = agentService;
        this.agentProvider = agentProvider;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.guardrailPolicyFactory = guardrailPolicyFactory;
        this.sessionTitleGenerator = sessionTitleGenerator;
        this.contextManagerProvider = contextManagerProvider;

        this.runtimeCache =
                RefCountedCache.<String, AgentSessionRuntime>builder()
                        .name("agent-runtime")
                        .idleTimeout(DEFAULT_IDLE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                        .cleanupInterval(30, TimeUnit.SECONDS)
                        .build();
    }

    @Override
    public Flowable<Event> run(final AgentRequest request) {
        final AgentSessionRuntime runtime = getOrStartRuntime(request.getAgentId(), request.getSessionId());
        final String sessionId = runtime.sessionId();
        try {
            LOG.debug(
                    "run - session_id={} text=\"{}\"",
                    sessionId,
                    StringUtils.substring(request.getMessage(), 0, 50));
            final int maxCalls =
                    runtime.agentConfig() == null || runtime.agentConfig().getRuntime() == null
                            ? 50
                            : Math.max(1, runtime.agentConfig().getRuntime().getMaxSteps());
            final RunConfig runConfig =
                    RunConfig.builder().setStreamingMode(SSE).setMaxLlmCalls(maxCalls).build();
            final Runner runner = runtime.runner();
            return runner
                    .runAsync(
                            DEFAULT_USER_ID,
                            sessionId,
                            buildUserContent(request.getMessage()),
                            runConfig)
                    .doOnComplete(() -> updateTitle(runner, sessionId)).doFinally(() -> markRunInactive(sessionId));
        } catch (Exception ex) {
            markRunInactive(sessionId);
            return Flowable.error(ex);
        }
    }

    public void onSessionDeleted(@Observes final SessionDeletedEvent event) {
        runtimeCache.invalidate(event.sessionId());
    }

    private void updateTitle(final Runner runner, final String sessionId) {
        final Session session =
                Objects.requireNonNull(
                        runner
                                .sessionService()
                                .getSession(runner.appName(), DEFAULT_USER_ID, sessionId, Optional.empty())
                                .blockingGet());
        sessionTitleGenerator
                .generateTitle(session.events())
                .ifPresent(title -> sessionService.updateTitle(sessionId, title));
    }

    private static Content buildFromText(final String text) {
        return Content.fromParts(fromText(text));
    }

    private static Content buildUserContent(final String message) {
        return buildFromText(message);
    }

    private AgentSessionRuntime getOrStartRuntime(final String agentId, final String sessionId) {
        final AgentSession session =
                StringUtils.isNotBlank(sessionId) ? sessionService.getSession(sessionId).orElse(null) : null;
        final BaseAgentConfig agentConfig = getAgentConfig(agentId, session);

        final String resolvedSessionId =
                StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
        return runtimeCache.getAndAcquire(
                resolvedSessionId, ignored -> createRuntime(resolvedSessionId, agentConfig, session == null));
    }

    private void markRunInactive(final String sessionId) {
        runtimeCache.release(sessionId);
    }

    private AgentSessionRuntime createRuntime(final String sessionId, final BaseAgentConfig agentConfig, final boolean createSession) {

        if (createSession) {
            LOG.debug(
                    "Creating new session for agent_id={} session_id={}", agentConfig.getId(), sessionId);
            sessionRepository
                    .createSession(agentConfig.getId(), DEFAULT_USER_ID, buildInitialState(), sessionId)
                    .blockingGet();
        }

        final Agent agent = agentProvider.get(agentConfig);
        final PluginGroup pluginGroup = buildPluginGroup(agent);
        final App.Builder appBuilder = App.builder().name(agentConfig.getId()).rootAgent(agent).plugins(List.of(pluginGroup)).resumabilityConfig(new ResumabilityConfig(isResumable(agentConfig)));

        final Runner runner = Runner.builder().app(appBuilder.build()).sessionService(sessionRepository).build();
        return new AgentSessionRuntime(sessionId, runner, agentConfig);
    }

    private static boolean isResumable(final BaseAgentConfig config) {
        if (config == null || config.getRuntime() == null) {
            return true;
        }
        return config.getRuntime().isResumable();
    }

    private BaseAgentConfig getAgentConfig(final String agentId, final AgentSession session) {
        return getConfig(session == null ? agentId : session.getAgentId());
    }

    private BaseAgentConfig getConfig(final String agentId) {
        if (StringUtils.isBlank(agentId)) {
            throw new IllegalArgumentException("agentId cannot be blank");
        }
        return agentService
                .getAgent(agentId)
                .orElseThrow(() -> new AssetNotFoundException("agent", agentId));
    }

    private PluginGroup buildPluginGroup(final Agent rootAgent) {
        final Queue<Agent> queue = new LinkedList<>();
        queue.add(rootAgent);

        final Set<String> visited = new HashSet<>();
        final Map<String, GuardrailPolicyFactory.GuardrailPolicy> policies = new LinkedHashMap<>();
        final Map<String, ContextManager> contextManagers = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            final Agent agent = queue.poll();
            if (!visited.add(agent.name())) {
                continue;
            }
            policies.put(agent.name(), guardrailPolicyFactory.build(agent.getAgentConfig().getGuardrails()));
            contextManagers.put(agent.name(), contextManagerProvider.get(agent.getAgentConfig()));

            for (final BaseAgent subAgent : agent.subAgents()) {
                if (subAgent instanceof Agent llmSubAgent) {
                    queue.add(llmSubAgent);
                }
            }
        }
        return new PluginGroup(
                "engine",
                List.of(
                        new GuardrailPlugin(policies),
                        new AgentRunLifecyclePlugin(),
                        new HumanInTheLoopPlugin(),
                        new ModelInvocationPipeline(),
                        new ContextManagementPlugin(contextManagers)));
    }

}
