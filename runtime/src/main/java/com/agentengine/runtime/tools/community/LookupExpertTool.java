package com.agentengine.runtime.tools.community;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.runtime.api.services.CommunityRegistry;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up expert agents available in the community.
 *
 * <p>Returns all registered experts with their IDs, names, descriptions, and capabilities. Agents
 * can use this to discover which experts are available and what they can do, then invoke them via
 * spawn_agent.
 */
@DiscoverableTool
public final class LookupExpertTool extends Tool {

    private static final String TOOL_NAME = "lookup_expert";

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Returns the catalog of expert agents registered in the community, including each expert's identifier, "
                    + "name, description, and declared capabilities. Use this to discover which specialised agents "
                    + "are available and what tasks they are suited for before delegating work to one. expert_id "
                    + "is the identifier to provide when invoking that expert. "
                    + "Returns: { experts: [{expert_id, name, description, capabilities}], count }.");

    private final CommunityRegistry communityRegistry;

    public LookupExpertTool(final CommunityRegistry communityRegistry) {
        super(DESCRIPTOR);
        this.communityRegistry = communityRegistry;
    }

    public Map<String, Object> execute(
            @ToolSchema(
                            name = "query",
                            description =
                                    "Optional search hint. Not yet active — all registered experts are always returned "
                                            + "regardless of this value.",
                            optional = true)
                    final String query) {
        final List<BaseAgentConfig> experts = communityRegistry.findExperts(query);
        final List<Map<String, Object>> expertList = new ArrayList<>();
        for (final BaseAgentConfig expert : experts) {
            final Map<String, Object> expertMap = new HashMap<>();
            expertMap.put("expert_id", expert.getId());
            expertMap.put("name", expert.getName());
            expertMap.put("description", expert.getDescription());
            expertMap.put("capabilities", expert.getCapabilities());
            expertList.add(expertMap);
        }

        return Map.of("experts", expertList, "count", expertList.size());
    }
}
