package com.agentengine.connectors.http;

import com.agentengine.connectors.http.auth.BasicAuthDecoratorSpec;
import com.agentengine.connectors.http.auth.HeaderAuthDecoratorSpec;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.infra.beans.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.connectors.infra.utils.ConnectorCodecJacksonTypeProvider;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class HttpConnectorCodecJacksonTypeProvider implements ConnectorCodecJacksonTypeProvider {

  @Override
  public List<NamedType> getTypes() {
    return List.of(
        new NamedType(HttpExecutorSpec.class, ExecutorSpec.Type.HTTP.name()),
        new NamedType(HeaderAuthDecoratorSpec.class, AuthDecoratorSpec.Type.HEADER.name()),
        new NamedType(BasicAuthDecoratorSpec.class, AuthDecoratorSpec.Type.BASIC.name()));
  }
}
