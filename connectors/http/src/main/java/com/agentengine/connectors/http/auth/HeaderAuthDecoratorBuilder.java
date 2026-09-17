package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.http.beans.HttpRequest;
import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.connectors.infra.beans.AuthDecoratorSpec;
import com.agentengine.connectors.infra.builders.AuthDecoratorBuilder;
import com.agentengine.util.common.Cache;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import com.google.common.cache.CacheBuilder;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class HeaderAuthDecoratorBuilder
    implements AuthDecoratorBuilder<HeaderAuthDecoratorSpec, Object, HttpRequest> {

  private final Instance<ConnectionService> connectionService;
  private final Cache<HeaderAuthDecoratorSpec, AuthDecorator<Object, HttpRequest>> decoratorCache =
      new Cache<>(CacheBuilder.newBuilder(), this::buildDecorator);

  @Inject
  public HeaderAuthDecoratorBuilder(Instance<ConnectionService> connectionService) {
    this.connectionService = connectionService;
  }

  @Override
  public AuthDecorator<Object, HttpRequest> build(HeaderAuthDecoratorSpec spec) {
    return decoratorCache.get(spec);
  }

  @Override
  public AuthDecoratorSpec.Type getType() {
    return AuthDecoratorSpec.Type.HEADER;
  }

  private AuthDecorator<Object, HttpRequest> buildDecorator(HeaderAuthDecoratorSpec spec) {
    final Template<Map<String, String>> headerTemplate =
        TemplateUtils.buildTemplate(spec.getHeaders());
    return new HeaderAuthDecorator(headerTemplate, connectionService.get());
  }
}
