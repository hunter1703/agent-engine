package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.api.model.ResourceGrants;
import com.agentengine.agent.infra.tools.ToolFactory;
import com.agentengine.agent.infra.utils.ExtendedRunConfig;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.tools.BaseTool;
import io.reactivex.rxjava3.core.Maybe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Grants a session's own knowledge-access tools on demand, independent of whether the agent's own
 * config declares them.
 *
 * <p>A file or indexed knowledge item can be attached to any agent at request time via {@code
 * ResourceGrants}, regardless of what its author anticipated when writing its static tool list — so
 * tool availability has to follow the grant, not the config. This adds {@code
 * read_knowledge_source} to the request when the session has knowledge-source grants, and {@code
 * search_knowledge} when it has knowledge-id grants, skipping either one the agent's own config
 * already declares.
 */
public final class KnowledgeAccessPlugin extends BasePlugin {

  private final BaseTool readKnowledgeSourceTool;
  private final BaseTool searchKnowledgeTool;

  public KnowledgeAccessPlugin(final ToolFactory toolFactory) {
    super("knowledge_access_plugin");
    this.readKnowledgeSourceTool = toolFactory.getReadKnowledgeSourceTool();
    this.searchKnowledgeTool = toolFactory.getSearchKnowledgeTool();
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder llmRequestBuilder) {
    if (!(callbackContext.invocationContext().runConfig()
        instanceof ExtendedRunConfig extendedRunConfig)) {
      return Maybe.empty();
    }

    final Map<String, BaseTool> declaredTools = llmRequestBuilder.build().tools();
    final List<BaseTool> toolsToGrant = new ArrayList<>(2);
    final ResourceGrants grants = extendedRunConfig.grants();
    if (CollectionUtils.isNotEmpty(grants.knowledgeSources())
        && !declaredTools.containsKey(Constants.ToolNames.READ_KNOWLEDGE_SOURCE)) {
      toolsToGrant.add(readKnowledgeSourceTool);
    }
    if (CollectionUtils.isNotEmpty(grants.knowledgeIds())
        && !declaredTools.containsKey(Constants.ToolNames.SEARCH_KNOWLEDGE)) {
      toolsToGrant.add(searchKnowledgeTool);
    }
    if (!toolsToGrant.isEmpty()) {
      llmRequestBuilder.appendTools(toolsToGrant);
    }
    return Maybe.empty();
  }
}
