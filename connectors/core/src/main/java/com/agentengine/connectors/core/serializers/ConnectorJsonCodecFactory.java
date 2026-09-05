package com.agentengine.connectors.core.serializers;

import com.agentengine.connectors.http.auth.HeaderAuthDecoratorSpec;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.util.common.JsonCodecFactory;
import com.agentengine.util.common.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import jakarta.inject.Singleton;

@Singleton
public class ConnectorJsonCodecFactory implements JsonCodecFactory {

  private static final int PRIORITY = 0;

  @Override
  public ObjectMapper getCodec() {
    final ObjectMapper mapper = JsonUtils.copyMapper();
    mapper.registerSubtypes(new NamedType(HttpExecutorSpec.class, ExecutorSpec.Type.HTTP.name()));
    mapper.registerSubtypes(
        new NamedType(HeaderAuthDecoratorSpec.class, AuthDecoratorSpec.Type.HEADER.name()));
    return mapper;
  }

  @Override
  public int priority() {
    return PRIORITY;
  }
}
