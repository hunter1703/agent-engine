package com.agentengine.engine.plugins;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rebuilds model prompt contents using configured context managers per agent.
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
    final InvocationContext invocationContext =
        callbackContext == null ? null : callbackContext.invocationContext();
    final LlmRequest request = requestBuilder.build();
    final LlmRequest updatedRequest = applyContextManager(request, invocationContext);
    requestBuilder.contents(updatedRequest.contents());
    return Maybe.empty();
  }

  private LlmRequest applyContextManager(
      final LlmRequest request, final InvocationContext invocationContext) {
    if (request == null || invocationContext == null) {
      return request;
    }
    final String agentId =
        invocationContext.agent() == null ? null : invocationContext.agent().name();
    final ContextManager contextManager = contextManagerByAgentId.get(agentId);
    if (contextManager == null) {
      return request;
    }
    try {
      final List<Content> contents = CollectionUtils.nullSafeList(request.contents());
      final List<Content> prompt =
          contextManager.buildPrompt(agentId, invocationContext.session().id(), contents);
      return request.toBuilder().contents(prompt).build();
    } catch (Exception ex) {
      LOG.warn(
          "Context manager failed for agent_id={} session_id={}; continuing with unmodified prompt.",
          agentId,
          invocationContext.session() == null ? null : invocationContext.session().id(),
          ex);
      return request;
    }
  }
}
