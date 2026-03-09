package com.agentengine.connectors.core.http;

public interface HttpTransport {

  HttpResponseData execute(HttpRequestData request);
}
