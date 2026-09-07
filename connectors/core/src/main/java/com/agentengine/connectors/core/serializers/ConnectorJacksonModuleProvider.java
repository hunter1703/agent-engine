package com.agentengine.connectors.core.serializers;

import com.agentengine.connectors.http.auth.HeaderAuthDecoratorSpec;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.util.common.CodecModuleProvider;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.inject.Singleton;

@Singleton
public final class ConnectorJacksonModuleProvider implements CodecModuleProvider {

  @Override
  public Module getModule() {
    return new ConnectorJacksonModule();
  }

  private static final class ConnectorJacksonModule extends SimpleModule {

    private ConnectorJacksonModule() {
      super(ConnectorJacksonModule.class.getSimpleName());
    }

    @Override
    public void setupModule(final SetupContext context) {
      super.setupModule(context);
      context.registerSubtypes(
          new NamedType(HttpExecutorSpec.class, ExecutorSpec.Type.HTTP.name()),
          new NamedType(HeaderAuthDecoratorSpec.class, AuthDecoratorSpec.Type.HEADER.name()));
    }
  }
}
