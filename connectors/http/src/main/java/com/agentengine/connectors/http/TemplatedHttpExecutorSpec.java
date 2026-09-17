package com.agentengine.connectors.http;

import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.infra.TemplatedExecutorSpec;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import java.util.Map;

public class TemplatedHttpExecutorSpec
    extends TemplatedExecutorSpec<HttpExecutorSpec, Map<String, Object>, HttpExecutorSpec> {
  private final Template<String> baseUrl;
  private final Template<String> path;
  private final Template<String> method;
  private final Template<Map<String, Object>> body;
  private final Template<Map<String, Object>> queryParams;
  private final Template<Map<String, String>> headers;

  public TemplatedHttpExecutorSpec(HttpExecutorSpec executorSpec) {
    super(executorSpec);
    this.baseUrl = TemplateUtils.buildTemplate(executorSpec.getBaseUrl());
    this.path = TemplateUtils.buildTemplate(executorSpec.getPath());
    this.method = TemplateUtils.buildTemplate(executorSpec.getMethod());
    this.body = TemplateUtils.buildTemplate(executorSpec.getBody());
    this.queryParams = TemplateUtils.buildTemplate(executorSpec.getQueryParams());
    this.headers = TemplateUtils.buildTemplate(executorSpec.getHeaders());
  }

  @Override
  public HttpExecutorSpec evaluate(Map<String, Object> params) {
    final Map<String, Object> context =
        Map.of(ConnectorConstants.INPUT, CollectionUtils.nullSafeMap(params));
    HttpExecutorSpec executorSpec = new HttpExecutorSpec();
    executorSpec.setBaseUrl(baseUrl.getValue(context));
    executorSpec.setPath(path.getValue(context));
    executorSpec.setMethod(method.getValue(context));
    executorSpec.setBody(body == null ? Map.of() : body.getValue(context));
    executorSpec.setQueryParams(queryParams == null ? Map.of() : queryParams.getValue(context));
    executorSpec.setHeaders(headers == null ? Map.of() : headers.getValue(context));
    return executorSpec;
  }
}
