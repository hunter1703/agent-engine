package com.agentengine.connectors.http.builders;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.http.HttpClientProvider;
import com.agentengine.connectors.http.TemplatedHttpExecutorSpec;
import com.agentengine.connectors.http.beans.HttpClientOptions;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.http.beans.HttpRequest;
import com.agentengine.connectors.http.executor.HttpConnectorExecutor;
import com.agentengine.connectors.infra.ClientProvider;
import com.agentengine.connectors.infra.auth.AuthDecorator;
import com.agentengine.connectors.infra.beans.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.connectors.infra.builders.AuthDecoratorFactory;
import com.agentengine.connectors.infra.builders.BuildContext;
import com.agentengine.connectors.infra.builders.ConnectorExecutorBuilder;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import jakarta.inject.Singleton;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class HttpConnectorExecutorBuilder
    implements ConnectorExecutorBuilder<
        HttpExecutorSpec, Map<String, Object>, Map<String, Object>> {
  private static final Logger LOG = LoggerFactory.getLogger(HttpConnectorExecutorBuilder.class);
  private final ClientProvider<HttpClientOptions, OkHttpClient> clientProvider;
  private final AuthDecoratorFactory authDecoratorFactory;

  public HttpConnectorExecutorBuilder(
      HttpClientProvider clientProvider, AuthDecoratorFactory authDecoratorFactory) {
    this.clientProvider = clientProvider;
    this.authDecoratorFactory = authDecoratorFactory;
  }

  @Override
  public ConnectorExecutor<Map<String, Object>, Map<String, Object>> build(
      BuildContext<HttpExecutorSpec> buildContext) {
    final Connection connection = buildContext.connection();
    final String authType = connection == null ? null : connection.getAuthType();
    final ConnectorSpec connectorSpec = buildContext.connectorSpec();
    final AuthDecoratorSpec authDecoratorSpec =
        CollectionUtils.getValueFromMap(connectorSpec.getAuth(), authType);
    LOG.debug(
        "Resolving auth decorator: authType={} availableAuthKeys={} resolvedSpecType={}",
        authType,
        connectorSpec.getAuth() == null ? null : connectorSpec.getAuth().keySet(),
        authDecoratorSpec == null ? null : authDecoratorSpec.getType());
    final AuthDecorator<Object, HttpRequest> decorator =
        StringUtils.isNotBlank(authType)
            ? authDecoratorFactory.build(authDecoratorSpec)
            : AuthDecorator.noop();
    return new HttpConnectorExecutor(
        new TemplatedHttpExecutorSpec(buildContext.spec()), clientProvider, decorator);
  }

  @Override
  public ExecutorSpec.Type getType() {
    return ExecutorSpec.Type.HTTP;
  }
}
