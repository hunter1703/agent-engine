package com.agentengine.connectors.http.builders;

import com.agentengine.connectors.http.HttpClientProvider;
import com.agentengine.connectors.http.TemplatedHttpConnectorSpec;
import com.agentengine.connectors.http.beans.HttpClientOptions;
import com.agentengine.connectors.http.beans.HttpConnectorSpec;
import com.agentengine.connectors.http.executor.HttpConnectorExecutor;
import com.agentengine.connectors.infra.ClientProvider;
import com.agentengine.connectors.infra.auth.AuthDecoratorFactory;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.builders.ConnectorExecutorBuilder;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.OkHttpClient;

@Singleton
public class HttpConnectorExecutorBuilder
    implements ConnectorExecutorBuilder<
        HttpConnectorSpec, Map<String, Object>, Map<String, Object>> {
  private final ConcurrentHashMap<HttpConnectorSpec, HttpConnectorExecutor> executorCache =
      new ConcurrentHashMap<>();
  private final ClientProvider<HttpClientOptions, OkHttpClient> clientProvider;
  private final AuthDecoratorFactory authDecoratorFactory;

  public HttpConnectorExecutorBuilder(
      HttpClientProvider clientProvider, AuthDecoratorFactory authDecoratorFactory) {
    this.clientProvider = clientProvider;
    this.authDecoratorFactory = authDecoratorFactory;
  }

  @Override
  public ConnectorExecutor<Map<String, Object>, Map<String, Object>> build(HttpConnectorSpec spec) {
    return executorCache.computeIfAbsent(
        spec,
        _ ->
            new HttpConnectorExecutor(
                new TemplatedHttpConnectorSpec(spec),
                clientProvider,
                authDecoratorFactory.build(spec.getAuth())));
  }

  @Override
  public ConnectorSpec.Type getType() {
    return ConnectorSpec.Type.HTTP;
  }
}
