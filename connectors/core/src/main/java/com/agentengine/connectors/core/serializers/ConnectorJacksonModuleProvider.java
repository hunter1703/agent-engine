package com.agentengine.connectors.core.serializers;

import com.agentengine.connectors.infra.utils.ConnectorCodecJacksonTypeProvider;
import com.agentengine.util.common.CodecModuleProvider;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class ConnectorJacksonModuleProvider implements CodecModuleProvider {

  private final List<ConnectorCodecJacksonTypeProvider> providerList;

  @Inject
  public ConnectorJacksonModuleProvider(
      final Instance<ConnectorCodecJacksonTypeProvider> providers) {
    this.providerList = providers.stream().toList();
  }

  @Override
  public Module getModule() {
    return new ConnectorJacksonModule(providerList);
  }

  private static final class ConnectorJacksonModule extends SimpleModule {

    private final List<ConnectorCodecJacksonTypeProvider> providerList;

    private ConnectorJacksonModule(List<ConnectorCodecJacksonTypeProvider> providerList) {
      super(ConnectorJacksonModule.class.getSimpleName());
      this.providerList = providerList;
    }

    @Override
    public void setupModule(final SetupContext context) {
      super.setupModule(context);
      final NamedType[] namedTypes =
          providerList.stream()
              .map(ConnectorCodecJacksonTypeProvider::getTypes)
              .flatMap(List::stream)
              .toArray(_ -> new NamedType[0]);
      context.registerSubtypes(namedTypes);
    }
  }
}
