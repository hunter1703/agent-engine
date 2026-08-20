package com.agentengine.connectors.http.builders;

import com.agentengine.connectors.http.HttpClientProvider;
import com.agentengine.connectors.http.TemplatedHttpExecutorSpec;
import com.agentengine.connectors.http.beans.HttpClientOptions;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.http.executor.HttpConnectorExecutor;
import com.agentengine.connectors.infra.ClientProvider;
import com.agentengine.connectors.infra.auth.AuthDecoratorFactory;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.connectors.infra.builders.ConnectorExecutorBuilder;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.OkHttpClient;

@Singleton
public class HttpConnectorExecutorBuilder
    implements ConnectorExecutorBuilder<
        HttpExecutorSpec, Map<String, Object>, Map<String, Object>> {
  private final ConcurrentHashMap<HttpExecutorSpec, HttpConnectorExecutor> executorCache =
      new ConcurrentHashMap<>();
  private final ClientProvider<HttpClientOptions, OkHttpClient> clientProvider;
  private final AuthDecoratorFactory authDecoratorFactory;

  public HttpConnectorExecutorBuilder(
      HttpClientProvider clientProvider, AuthDecoratorFactory authDecoratorFactory) {
    this.clientProvider = clientProvider;
    this.authDecoratorFactory = authDecoratorFactory;
  }

  @Override
  public ConnectorExecutor<Map<String, Object>, Map<String, Object>> build(
      HttpExecutorSpec spec, ConnectorSpec connectorSpec) {
    return executorCache.computeIfAbsent(
        spec,
        _ ->
            new HttpConnectorExecutor(
                new TemplatedHttpExecutorSpec(spec),
                clientProvider,
                authDecoratorFactory.build(connectorSpec.getAuth())));
  }

  @Override
  public ExecutorSpec.Type getType() {
    return ExecutorSpec.Type.HTTP;
  }
}
