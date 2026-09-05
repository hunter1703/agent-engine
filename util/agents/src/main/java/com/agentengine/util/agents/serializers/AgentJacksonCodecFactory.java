package com.agentengine.util.agents.serializers;

import com.agentengine.util.common.JsonCodecFactory;
import com.agentengine.util.common.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;

@Singleton
public class AgentJacksonCodecFactory implements JsonCodecFactory {

  private static final int PRIORITY = 0;

  @Override
  public ObjectMapper getCodec() {
    return JsonUtils.copyMapper()
        .registerModule(new DefaultTypingJacksonModule())
        .registerModule(new AdkJacksonModule())
        .registerModule(new AGUIJacksonModule());
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}
