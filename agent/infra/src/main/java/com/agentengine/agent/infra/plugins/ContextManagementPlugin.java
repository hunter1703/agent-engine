package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.context.ContextManager;
import com.agentengine.agent.infra.utils.ContentUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-processes model prompt contents before each LLM call.
 *
 * <p>Always strips thought parts from session history — thought traces must never be sent back to
 * the model as regular context. Optionally reshapes the remaining contents through a per-agent
 * {@link ContextManager} (e.g. last-N windowing, compaction).
 */
public final class ContextManagementPlugin extends BasePlugin {
    private static final Logger LOG = LoggerFactory.getLogger(ContextManagementPlugin.class);
    private static final String NAME = "context_management";

    private final Map<String, ContextManager> contextManagerByAgentId;

    public ContextManagementPlugin(final Map<String, ContextManager> contextManagerByAgentId) {
        super(NAME);
        this.contextManagerByAgentId = Map.copyOf(contextManagerByAgentId);
    }

    @Override
    public Maybe<LlmResponse> beforeModelCallback(
            final CallbackContext callbackContext, final LlmRequest.Builder requestBuilder) {
        final List<Content> stripped =
                ContentUtils.stripThoughtParts(requestBuilder.build().contents());
        requestBuilder.contents(stripped);
        applyContextManager(stripped, callbackContext.invocationContext()).ifPresent(requestBuilder::contents);
        return Maybe.empty();
    }

    private Optional<List<Content>> applyContextManager(
            final List<Content> contents, final InvocationContext invocationContext) {
        final String agentId = invocationContext.agent().name();
        final ContextManager contextManager = contextManagerByAgentId.get(agentId);
        if (contextManager == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(contextManager.buildPrompt(
                    agentId, invocationContext.session().id(), contents));
        } catch (Exception ex) {
            LOG.warn(
                    "Context manager failed for agent_id={} session_id={}; continuing with stripped prompt.",
                    agentId,
                    invocationContext.session().id(),
                    ex);
            return Optional.empty();
        }
    }
}
