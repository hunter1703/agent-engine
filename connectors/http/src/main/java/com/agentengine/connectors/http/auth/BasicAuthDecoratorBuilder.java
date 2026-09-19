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

@Singleton
public class BasicAuthDecoratorBuilder
    implements AuthDecoratorBuilder<BasicAuthDecoratorSpec, Object, HttpRequest> {

  private final Instance<ConnectionService> connectionService;
  private final Cache<BasicAuthDecoratorSpec, AuthDecorator<Object, HttpRequest>> decoratorCache = new Cache<>(CacheBuilder.newBuilder(), this::buildDecorator);

  @Inject
  public BasicAuthDecoratorBuilder(Instance<ConnectionService> connectionService) {
    this.connectionService = connectionService;
  }

  @Override
  public AuthDecorator<Object, HttpRequest> build(BasicAuthDecoratorSpec spec) {
    return decoratorCache.get(spec);
  }

  @Override
  public AuthDecoratorSpec.Type getType() {
    return AuthDecoratorSpec.Type.BASIC;
  }

  private AuthDecorator<Object, HttpRequest> buildDecorator(BasicAuthDecoratorSpec spec) {
    final Template<String> usernameTemplate = TemplateUtils.buildTemplate(spec.getUsername());
    final Template<String> passwordTemplate = TemplateUtils.buildTemplate(spec.getPassword());
    return new BasicAuthDecorator(usernameTemplate, passwordTemplate, connectionService.get());
  }
}
