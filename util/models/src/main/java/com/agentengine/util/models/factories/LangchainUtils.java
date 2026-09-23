package com.agentengine.util.models.factories;

import com.agentengine.util.common.ThreadUtils;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;

public final class LangchainUtils {
  private static final ExecutorService HTTP_EXECUTOR =
      ThreadUtils.newVirtualThreadExecutor("langchain-http-");

  private LangchainUtils() {}

  public static HttpClientBuilder httpClientBuilder() {
    return new JdkHttpClientBuilder()
        .httpClientBuilder(HttpClient.newBuilder().executor(HTTP_EXECUTOR));
  }
}
