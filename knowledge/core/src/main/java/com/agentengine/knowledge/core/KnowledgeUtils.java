package com.agentengine.knowledge.core;

import com.agentengine.knowledge.api.beans.KnowledgeChunk;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.GrantUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.Permission;
import com.agentengine.util.context.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class KnowledgeUtils {

  private KnowledgeUtils() {}

  public static List<String> readGrants(final Map<String, Object> additional) {
    final String agentId =
        CollectionUtils.getStringValueFromMap(additional, KnowledgeChunk.ADDITIONAL_AGENT_ID);
    final List<String> grants = new ArrayList<>();
    if (StringUtils.isNotBlank(agentId)) {
      grants.add(GrantUtils.build(Permission.READ, AssetClass.AGENT, agentId));
    }

    final String sessionId =
        CollectionUtils.getStringValueFromMap(additional, KnowledgeChunk.ADDITIONAL_SESSION_ID);
    if (StringUtils.isNotBlank(sessionId)) {
      grants.add(GrantUtils.build(Permission.READ, AssetClass.AGENT_SESSION, sessionId));
    }

    final Optional<Integer> userIdOpt = Context.userId();
    if (StringUtils.isNotBlank(agentId) && userIdOpt.isPresent()) {
      grants.add(
          GrantUtils.build(
              Permission.READ,
              AssetClass.AGENT,
              agentId,
              AssetClass.USER,
              Integer.toString(userIdOpt.get())));
    }
    return grants;
  }
}
