package com.agentengine.connectors.http.auth;

import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.http.beans.HttpRequest;
import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.connectors.infra.auth.AuthDecoratorBuilder;
import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class HeaderAuthDecoratorBuilder
    implements AuthDecoratorBuilder<HeaderAuthDecoratorSpec, HttpRequest> {

  private final Instance<ConnectionService> connectionService;

  @Inject
  public HeaderAuthDecoratorBuilder(Instance<ConnectionService> connectionService) {
    this.connectionService = connectionService;
  }

  @Override
  public AuthDecorator<HttpRequest> build(HeaderAuthDecoratorSpec spec) {
    final Template<Map<String, String>> headerTemplate =
        TemplateUtils.buildTemplate(spec.getHeaders());
    return new HeaderAuthDecorator(headerTemplate, connectionService.get());
  }

  @Override
  public AuthDecoratorSpec.Type getType() {
    return AuthDecoratorSpec.Type.HEADER;
  }
}
