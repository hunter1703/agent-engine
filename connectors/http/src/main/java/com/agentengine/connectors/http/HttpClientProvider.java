package com.agentengine.connectors.http;

import com.agentengine.connectors.http.beans.HttpClientOptions;
import com.agentengine.connectors.infra.ClientProvider;
import okhttp3.OkHttpClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class HttpClientProvider implements ClientProvider<HttpClientOptions, OkHttpClient> {
    private final ConcurrentMap<HttpClientOptions, OkHttpClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public OkHttpClient getClient(HttpClientOptions options) {
        return clientCache.computeIfAbsent(options, _ -> new OkHttpClient());
    }
}
