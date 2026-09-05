package com.agentengine.util.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;

@Singleton
public class DefaultJsonCodecFactory implements JsonCodecFactory {

  @Override
  public ObjectMapper getCodec() {
    return JsonUtils.copyMapper();
  }

  @Override
  public int priority() {
    return Integer.MAX_VALUE;
  }
}
