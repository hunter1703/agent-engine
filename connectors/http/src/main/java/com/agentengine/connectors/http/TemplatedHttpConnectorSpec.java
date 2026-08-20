package com.agentengine.connectors.http;

import com.agentengine.connectors.http.beans.HttpConnectorSpec;
import com.agentengine.connectors.infra.TemplatedConnectorSpec;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import java.util.Map;

public class TemplatedHttpConnectorSpec
    extends TemplatedConnectorSpec<HttpConnectorSpec, Map<String, Object>, HttpConnectorSpec> {
  private final Template<String> baseUrl;
  private final Template<String> path;
  private final Template<String> method;
  private final Template<Map<String, Object>> body;
  private final Template<Map<String, Object>> queryParams;
  private final Template<Map<String, String>> headers;

  public TemplatedHttpConnectorSpec(HttpConnectorSpec connectorSpec) {
    super(connectorSpec);
    this.baseUrl = TemplateUtils.buildTemplate(connectorSpec.getBaseUrl());
    this.path = TemplateUtils.buildTemplate(connectorSpec.getPath());
    this.method = TemplateUtils.buildTemplate(connectorSpec.getMethod());
    this.body = TemplateUtils.buildTemplate(connectorSpec.getBody());
    this.queryParams = TemplateUtils.buildTemplate(connectorSpec.getQueryParams());
    this.headers = TemplateUtils.buildTemplate(connectorSpec.getHeaders());
  }

  @Override
  public HttpConnectorSpec evaluate(Map<String, Object> params) {
    HttpConnectorSpec connectorSpec = new HttpConnectorSpec();
    connectorSpec.setBaseUrl(baseUrl.getValue(params));
    connectorSpec.setPath(path.getValue(params));
    connectorSpec.setMethod(method.getValue(params));
    connectorSpec.setBody(body == null ? Map.of() : body.getValue(params));
    connectorSpec.setQueryParams(queryParams == null ? Map.of() : queryParams.getValue(params));
    connectorSpec.setHeaders(headers == null ? Map.of() : headers.getValue(params));
    return connectorSpec;
  }
}
